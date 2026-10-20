/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Keeps a history of how people change their name, username, phone number and photo, and of
 * blocking them, on this device only. The last known state of each person is remembered; when
 * Telegram brings a newer one, the difference is written down as "old → new".
 * Everything runs on its own queue so the chat list never waits for it.
 */
public class HumoyContactChanges extends BaseController {

    public static final int TYPE_PHOTO = 0;
    public static final int TYPE_PHOTO_REMOVED = 1;
    public static final int TYPE_PHONE = 2;
    public static final int TYPE_NAME = 3;
    public static final int TYPE_USERNAME = 4;
    public static final int TYPE_BLOCKED = 5;
    public static final int TYPE_UNBLOCKED = 6;
    public static final int TYPES_COUNT = 7;
    public static final int ALL_TYPES = (1 << TYPES_COUNT) - 1;

    // whose changes are kept, as in Telegraph: everyone, contacts on this device, mutual contacts,
    // people picked by hand, or no one
    public static final int SCOPE_ALL = 0;
    public static final int SCOPE_CONTACTS = 1;
    public static final int SCOPE_MUTUAL = 2;
    public static final int SCOPE_CUSTOM = 3;
    public static final int SCOPE_NONE = 4;

    public static class Change {
        public long id;
        public long userId;
        public int type;
        public int date;
        public String oldValue;
        public String newValue;
        // the earlier photo, copied from the cache when it changed; null if it wasn't there
        public String photoPath;
        public boolean seen;
    }

    public static class Person {
        public long userId;
        public int count;
        public int unseen;
        public int lastDate;
    }

    private static class State {
        String name;
        String username;
        String phone;
        long photoId = -1;
        int blocked = -1;
    }

    private static final HumoyContactChanges[] Instance = new HumoyContactChanges[UserConfig.MAX_ACCOUNT_COUNT];
    private static final DispatchQueue queue = new DispatchQueue("humoyContactChanges");

    public static HumoyContactChanges getInstance(int num) {
        HumoyContactChanges localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (HumoyContactChanges.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new HumoyContactChanges(num);
                }
            }
        }
        return localInstance;
    }

    private SQLiteDatabase db;
    // queue only
    private HashMap<Long, State> states;
    private boolean notifyPosted;

    private HumoyContactChanges(int num) {
        super(num);
    }

    private SQLiteDatabase db() {
        if (db == null) {
            File dir = ApplicationLoader.getFilesDirFixed();
            String name = currentAccount == 0 ? "humoy_contacts.db" : "humoy_contacts" + currentAccount + ".db";
            db = SQLiteDatabase.openOrCreateDatabase(new File(dir, name), null);
            db.execSQL("CREATE TABLE IF NOT EXISTS state(uid INTEGER PRIMARY KEY, name TEXT, username TEXT, phone TEXT, photo_id INTEGER, blocked INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS changes(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, type INTEGER, date INTEGER, old TEXT, new TEXT, photo TEXT, seen INTEGER)");
            db.execSQL("CREATE INDEX IF NOT EXISTS changes_uid ON changes(uid)");
        }
        return db;
    }

    private HashMap<Long, State> states() {
        if (states == null) {
            states = new HashMap<>();
            try (Cursor c = db().rawQuery("SELECT uid, name, username, phone, photo_id, blocked FROM state", null)) {
                while (c.moveToNext()) {
                    State s = new State();
                    s.name = c.getString(1);
                    s.username = c.getString(2);
                    s.phone = c.getString(3);
                    s.photoId = c.isNull(4) ? -1 : c.getLong(4);
                    s.blocked = c.isNull(5) ? -1 : c.getInt(5);
                    states.put(c.getLong(0), s);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return states;
    }

    public static File getPhotoDirectory(int account) {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "humoy_contacts" + (account == 0 ? "" : String.valueOf(account)));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            new File(dir, ".nomedia").createNewFile();
        } catch (Exception ignore) {
        }
        return dir;
    }

    // ---- who is watched

    private boolean watches(TLRPC.User user) {
        if (user == null || user.min || user.bot || user.self || UserObject.isDeleted(user) || UserObject.isService(user.id) || user.support) {
            return false;
        }
        if (user.id == getUserConfig().getClientUserId()) {
            return false;
        }
        switch (HumoyConfig.contactChangesScope) {
            case SCOPE_ALL:
                return true;
            case SCOPE_CONTACTS:
                return user.contact;
            case SCOPE_MUTUAL:
                return user.mutual_contact;
            case SCOPE_CUSTOM:
                return HumoyConfig.isContactChangesWatched(user.id);
            default:
                return false;
        }
    }

    private static boolean records(int type) {
        return (HumoyConfig.contactChangesTypes & (1 << type)) != 0;
    }

    private static String nameOf(String first, String last) {
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }

    private static String usernameOf(TLRPC.User user) {
        if (user.usernames != null && !user.usernames.isEmpty()) {
            ArrayList<String> names = new ArrayList<>();
            for (TLRPC.TL_username u : user.usernames) {
                if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                    names.add("@" + u.username);
                }
            }
            if (!names.isEmpty()) {
                return TextUtils.join(", ", names);
            }
        }
        return TextUtils.isEmpty(user.username) ? "" : "@" + user.username;
    }

    private static String usernameOf(ArrayList<TLRPC.TL_username> usernames) {
        ArrayList<String> names = new ArrayList<>();
        if (usernames != null) {
            for (TLRPC.TL_username u : usernames) {
                if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                    names.add("@" + u.username);
                }
            }
        }
        return TextUtils.join(", ", names);
    }

    private static long photoIdOf(TLRPC.UserProfilePhoto photo) {
        return photo == null || photo instanceof TLRPC.TL_userProfilePhotoEmpty ? 0 : photo.photo_id;
    }

    // ---- what Telegram tells us (any thread)

    /** A full user object from the server (not from the local cache). */
    public void onUser(TLRPC.User user) {
        if (!watches(user)) {
            return;
        }
        final long uid = user.id;
        // for contacts the name is the one saved in the address book, not theirs: updateUserName brings theirs
        final String name = user.contact ? null : nameOf(user.first_name, user.last_name);
        final String username = usernameOf(user);
        final String phone = TextUtils.isEmpty(user.phone) ? null : user.phone;
        // a photo only you see (set by you for a contact) says nothing about theirs
        final boolean photoKnown = user.photo == null || !user.photo.personal;
        final TLRPC.UserProfilePhoto photo = user.photo;
        queue.postRunnable(() -> {
            if (name != null) {
                compare(uid, TYPE_NAME, name);
            }
            compare(uid, TYPE_USERNAME, username);
            if (phone != null) {
                compare(uid, TYPE_PHONE, phone);
            }
            if (photoKnown) {
                comparePhoto(uid, null, photo);
            }
        });
    }

    public void onName(long uid, String first, String last, ArrayList<TLRPC.TL_username> usernames) {
        if (!watches(getMessagesController().getUser(uid))) {
            return;
        }
        String name = nameOf(first, last);
        String username = usernameOf(usernames);
        queue.postRunnable(() -> {
            State s = stateOf(uid);
            // the event comes when the name or the username changed; for contacts the earlier name
            // was never shown to us (the address book name is), so without a username change it's the name
            boolean usernameChanged = s.username != null && !TextUtils.equals(s.username, username);
            if (name != null) {
                compare(uid, TYPE_NAME, name, !usernameChanged);
            }
            compare(uid, TYPE_USERNAME, username);
        });
    }

    public void onPhone(long uid, String phone) {
        if (TextUtils.isEmpty(phone) || !watches(getMessagesController().getUser(uid))) {
            return;
        }
        queue.postRunnable(() -> compare(uid, TYPE_PHONE, phone));
    }

    /** @param oldPhoto the photo the app showed until now, to keep a copy of it */
    public void onPhoto(long uid, TLRPC.UserProfilePhoto oldPhoto, TLRPC.UserProfilePhoto newPhoto) {
        if (newPhoto != null && newPhoto.personal || !watches(getMessagesController().getUser(uid))) {
            return;
        }
        queue.postRunnable(() -> comparePhoto(uid, oldPhoto, newPhoto));
    }

    // uid -> when it was last fetched after an updateUser (UI thread)
    private final HashMap<Long, Long> refetched = new HashMap<>();

    /**
     * Telegram says a user changed but sends only the id (photo changes come like this); fetch the
     * user so {@link #onUser} compares the new photo now, not whenever the app happens to load it.
     */
    public void onUserChanged(long uid) {
        TLRPC.User user = getMessagesController().getUser(uid);
        if (!watches(user)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = refetched.get(uid);
        if (last != null && now - last < 5000) {
            return;
        }
        refetched.put(uid, now);
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        req.id.add(getMessagesController().getInputUser(user));
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof org.telegram.tgnet.Vector) {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                for (Object object : ((org.telegram.tgnet.Vector<?>) response).objects) {
                    if (object instanceof TLRPC.User) {
                        users.add((TLRPC.User) object);
                    }
                }
                if (!users.isEmpty()) {
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    AndroidUtilities.runOnUIThread(() -> {
                        getMessagesController().putUsers(users, false);
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_NAME);
                    });
                }
            }
        });
    }

    public void onBlocked(long uid, boolean blocked) {
        if (uid <= 0 || !watches(getMessagesController().getUser(uid))) {
            return;
        }
        queue.postRunnable(() -> {
            State s = stateOf(uid);
            int value = blocked ? 1 : 0;
            if (s.blocked == value) {
                return;
            }
            boolean first = s.blocked == -1;
            s.blocked = value;
            saveState(uid, s);
            // the first time we learn it, only a block is news; "not blocked" is how everyone starts
            if (!first || blocked) {
                int type = blocked ? TYPE_BLOCKED : TYPE_UNBLOCKED;
                if (records(type)) {
                    addChange(uid, type, null, null, null);
                }
            }
        });
    }

    // ---- comparing (queue)

    private State stateOf(long uid) {
        State s = states().get(uid);
        if (s == null) {
            s = new State();
            states().put(uid, s);
        }
        return s;
    }

    private void compare(long uid, int type, String value) {
        compare(uid, type, value, false);
    }

    /** @param evenIfUnknown write the change also when nothing was known before (old shows as —) */
    private void compare(long uid, int type, String value, boolean evenIfUnknown) {
        State s = stateOf(uid);
        String old = type == TYPE_NAME ? s.name : type == TYPE_USERNAME ? s.username : s.phone;
        if (TextUtils.equals(old, value)) {
            return;
        }
        boolean known = type == TYPE_NAME ? s.name != null : type == TYPE_USERNAME ? s.username != null : s.phone != null;
        if (type == TYPE_NAME) {
            s.name = value;
        } else if (type == TYPE_USERNAME) {
            s.username = value;
        } else {
            s.phone = value;
        }
        saveState(uid, s);
        // the first value seen is where watching starts, not a change
        if ((known || evenIfUnknown) && records(type)) {
            addChange(uid, type, old, value, null);
        }
    }

    private void comparePhoto(long uid, TLRPC.UserProfilePhoto oldPhoto, TLRPC.UserProfilePhoto newPhoto) {
        State s = stateOf(uid);
        long newId = photoIdOf(newPhoto);
        if (s.photoId == newId) {
            return;
        }
        boolean known = s.photoId != -1;
        long oldId = s.photoId;
        s.photoId = newId;
        saveState(uid, s);
        if (!known) {
            return;
        }
        int type = newId == 0 ? TYPE_PHOTO_REMOVED : TYPE_PHOTO;
        if (!records(type)) {
            return;
        }
        String path = null;
        if (oldId > 0) {
            if (oldPhoto == null || photoIdOf(oldPhoto) != oldId) {
                TLRPC.User user = getMessagesController().getUser(uid);
                oldPhoto = user != null && photoIdOf(user.photo) == oldId ? user.photo : null;
            }
            path = copyPhoto(uid, oldPhoto);
        }
        addChange(uid, type, null, null, path);
    }

    /** Copies the old photo from the cache, big if it was loaded, else small. */
    private String copyPhoto(long uid, TLRPC.UserProfilePhoto photo) {
        if (photo == null) {
            return null;
        }
        TLRPC.FileLocation[] locations = new TLRPC.FileLocation[]{photo.photo_big, photo.photo_small};
        for (TLRPC.FileLocation location : locations) {
            if (location == null) {
                continue;
            }
            File source = getFileLoader().getPathToAttach(location, true);
            if (source == null || !source.exists() || source.length() == 0) {
                source = getFileLoader().getPathToAttach(location, false);
            }
            if (source != null && source.exists() && source.length() > 0) {
                File dest = new File(getPhotoDirectory(currentAccount), uid + "_" + photo.photo_id + ".jpg");
                try {
                    if (dest.exists() || AndroidUtilities.copyFile(source, dest)) {
                        return dest.getAbsolutePath();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return null;
    }

    private void saveState(long uid, State s) {
        try {
            ContentValues v = new ContentValues();
            v.put("uid", uid);
            v.put("name", s.name);
            v.put("username", s.username);
            v.put("phone", s.phone);
            v.put("photo_id", s.photoId);
            v.put("blocked", s.blocked);
            db().insertWithOnConflict("state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void addChange(long uid, int type, String oldValue, String newValue, String photoPath) {
        try {
            ContentValues v = new ContentValues();
            v.put("uid", uid);
            v.put("type", type);
            v.put("date", getConnectionsManager().getCurrentTime());
            v.put("old", oldValue);
            v.put("new", newValue);
            v.put("photo", photoPath);
            v.put("seen", 0);
            db().insert("changes", null, v);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        if (!notifyPosted) {
            notifyPosted = true;
            AndroidUtilities.runOnUIThread(() -> {
                queue.postRunnable(() -> notifyPosted = false);
                getNotificationCenter().postNotificationName(NotificationCenter.humoyContactChanged);
            }, 300);
        }
    }

    // ---- reading (callbacks on the UI thread)

    public void loadPeople(Utilities.Callback<List<Person>> callback) {
        queue.postRunnable(() -> {
            ArrayList<Person> people = new ArrayList<>();
            try (Cursor c = db().rawQuery("SELECT uid, COUNT(*), SUM(CASE WHEN seen = 0 THEN 1 ELSE 0 END), MAX(date) FROM changes GROUP BY uid", null)) {
                while (c.moveToNext()) {
                    Person p = new Person();
                    p.userId = c.getLong(0);
                    p.count = c.getInt(1);
                    p.unseen = c.getInt(2);
                    p.lastDate = c.getInt(3);
                    people.add(p);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            boolean unseenFirst = HumoyConfig.contactChangesUnseenFirst;
            java.util.Collections.sort(people, (a, b) -> {
                if (unseenFirst && (a.unseen > 0) != (b.unseen > 0)) {
                    return a.unseen > 0 ? -1 : 1;
                }
                return Integer.compare(b.lastDate, a.lastDate);
            });
            AndroidUtilities.runOnUIThread(() -> callback.run(people));
        });
    }

    /** @param uid a person, or 0 for everyone; @param types bit mask of TYPE_ values */
    public void loadChanges(long uid, int types, Utilities.Callback<List<Change>> callback) {
        queue.postRunnable(() -> {
            ArrayList<Change> changes = new ArrayList<>();
            String where = uid != 0 ? "WHERE uid = " + uid : "";
            try (Cursor c = db().rawQuery("SELECT id, uid, type, date, old, new, photo, seen FROM changes " + where + " ORDER BY date DESC, id DESC", null)) {
                while (c.moveToNext()) {
                    int type = c.getInt(2);
                    if ((types & (1 << type)) == 0) {
                        continue;
                    }
                    Change ch = new Change();
                    ch.id = c.getLong(0);
                    ch.userId = c.getLong(1);
                    ch.type = type;
                    ch.date = c.getInt(3);
                    ch.oldValue = c.getString(4);
                    ch.newValue = c.getString(5);
                    ch.photoPath = c.getString(6);
                    ch.seen = c.getInt(7) != 0;
                    changes.add(ch);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(changes));
        });
    }

    public void markSeen(long uid) {
        queue.postRunnable(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put("seen", 1);
                if (uid != 0) {
                    db().update("changes", v, "uid = ? AND seen = 0", new String[]{String.valueOf(uid)});
                } else {
                    db().update("changes", v, "seen = 0", null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.humoyContactChanged));
        });
    }

    public void getUnseenCount(Utilities.Callback<Integer> callback) {
        queue.postRunnable(() -> {
            int count = 0;
            try (Cursor c = db().rawQuery("SELECT COUNT(*) FROM changes WHERE seen = 0", null)) {
                if (c.moveToNext()) {
                    count = c.getInt(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            int result = count;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    /** Deletes the history of one person (uid), or everyone's (0); what they look like now is kept. */
    public void clear(long uid) {
        queue.postRunnable(() -> {
            try {
                ArrayList<String> photos = new ArrayList<>();
                String where = uid != 0 ? "uid = " + uid + " AND " : "";
                try (Cursor c = db().rawQuery("SELECT photo FROM changes WHERE " + where + "photo IS NOT NULL", null)) {
                    while (c.moveToNext()) {
                        photos.add(c.getString(0));
                    }
                }
                if (uid != 0) {
                    db().delete("changes", "uid = ?", new String[]{String.valueOf(uid)});
                } else {
                    db().delete("changes", null, null);
                }
                for (String path : photos) {
                    new File(path).delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.humoyContactChanged));
        });
    }
}
