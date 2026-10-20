/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Keeps messages others delete, and the earlier texts of edited messages, on this device only.
 * Deleted messages simply stay in Telegram's own message cache; this store remembers which of them
 * were deleted. Earlier texts of edited messages are copied here before Telegram overwrites them.
 * Nothing here is ever sent anywhere.
 */
public class HumoyMessageStore extends BaseController {

    public static class EditVersion {
        public final String text;
        public final int date;
        // the whole earlier message, with its photo or file; null for versions saved before Humoy kept it
        public final TLRPC.Message message;

        EditVersion(String text, int date, TLRPC.Message message) {
            this.text = text;
            this.date = date;
            this.message = message;
        }
    }

    private static final HumoyMessageStore[] Instance = new HumoyMessageStore[UserConfig.MAX_ACCOUNT_COUNT];

    public static HumoyMessageStore getInstance(int num) {
        HumoyMessageStore localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (HumoyMessageStore.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new HumoyMessageStore(num);
                }
            }
        }
        return localInstance;
    }

    private SQLiteDatabase db;
    private final Object dbSync = new Object();
    private volatile boolean loaded;
    // dialog id -> ids of messages kept after deletion / with saved earlier texts
    private final ConcurrentHashMap<Long, Set<Integer>> deleted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Integer>> edited = new ConcurrentHashMap<>();

    private HumoyMessageStore(int num) {
        super(num);
    }

    private SQLiteDatabase db() {
        synchronized (dbSync) {
            if (db == null) {
                File dir = ApplicationLoader.getFilesDirFixed();
                String name = currentAccount == 0 ? "humoy_messages.db" : "humoy_messages" + currentAccount + ".db";
                db = SQLiteDatabase.openOrCreateDatabase(new File(dir, name), null);
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted(dialog_id INTEGER, mid INTEGER, date INTEGER, PRIMARY KEY(dialog_id, mid))");
                db.execSQL("CREATE TABLE IF NOT EXISTS edits(dialog_id INTEGER, mid INTEGER, date INTEGER, text TEXT)");
                db.execSQL("CREATE INDEX IF NOT EXISTS edits_idx ON edits(dialog_id, mid)");
                try {
                    db.execSQL("ALTER TABLE edits ADD COLUMN data BLOB");
                } catch (Exception ignore) {
                    // already there
                }
            }
            return db;
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (dbSync) {
            if (loaded) {
                return;
            }
            try {
                try (Cursor c = db().rawQuery("SELECT dialog_id, mid FROM deleted", null)) {
                    while (c.moveToNext()) {
                        ids(deleted, c.getLong(0)).add(c.getInt(1));
                    }
                }
                try (Cursor c = db().rawQuery("SELECT DISTINCT dialog_id, mid FROM edits", null)) {
                    while (c.moveToNext()) {
                        ids(edited, c.getLong(0)).add(c.getInt(1));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            loaded = true;
        }
    }

    private static Set<Integer> ids(ConcurrentHashMap<Long, Set<Integer>> map, long dialogId) {
        Set<Integer> set = map.get(dialogId);
        if (set == null) {
            set = Collections.newSetFromMap(new ConcurrentHashMap<>());
            Set<Integer> prev = map.putIfAbsent(dialogId, set);
            if (prev != null) {
                set = prev;
            }
        }
        return set;
    }

    private static boolean contains(ConcurrentHashMap<Long, Set<Integer>> map, long dialogId, int id) {
        Set<Integer> set = map.get(dialogId);
        return set != null && set.contains(id);
    }

    public boolean isDeleted(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.scheduled || messageObject.getId() <= 0) {
            return false;
        }
        ensureLoaded();
        return contains(deleted, messageObject.getDialogId(), messageObject.getId());
    }

    public boolean hasEditHistory(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.scheduled || messageObject.getId() <= 0) {
            return false;
        }
        ensureLoaded();
        return contains(edited, messageObject.getDialogId(), messageObject.getId());
    }

    /**
     * Called by MessagesController while it processes updates (not on the UI thread), before Telegram
     * deletes anything. Messages we have locally are kept and marked as deleted; the rest (unknown to
     * this device, or in Saved Messages) go on to be deleted as usual.
     *
     * @param deletedMessages dialog id (0 for private chats and basic groups) -> deleted message ids
     * @return what Telegram should still delete, or null if nothing
     */
    public LongSparseArray<ArrayList<Integer>> keepDeleted(LongSparseArray<ArrayList<Integer>> deletedMessages) {
        if (deletedMessages == null || !HumoyConfig.keepDeletedMessages) {
            return deletedMessages;
        }
        ensureLoaded();
        MessagesStorage storage = getMessagesStorage();
        long selfId = getUserConfig().getClientUserId();
        LongSparseArray<ArrayList<Integer>> kept = new LongSparseArray<>();
        // the same ids per key of deletedMessages, to tell what is left for Telegram to delete
        LongSparseArray<Set<Integer>> keptBySource = new LongSparseArray<>();
        CountDownLatch latch = new CountDownLatch(1);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                for (int a = 0, size = deletedMessages.size(); a < size; a++) {
                    long key = deletedMessages.keyAt(a);
                    ArrayList<Integer> list = deletedMessages.valueAt(a);
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    String ids = TextUtils.join(",", list);
                    if (key != 0) {
                        cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", ids, key));
                    } else {
                        cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM messages_v2 WHERE mid IN(%s) AND is_channel = 0", ids));
                    }
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        if (did == selfId || DialogObject.isEncryptedDialog(did)) {
                            continue;
                        }
                        ArrayList<Integer> mids = kept.get(did);
                        if (mids == null) {
                            kept.put(did, mids = new ArrayList<>());
                        }
                        int mid = cursor.intValue(1);
                        mids.add(mid);
                        Set<Integer> source = keptBySource.get(key);
                        if (source == null) {
                            keptBySource.put(key, source = new HashSet<>());
                        }
                        source.add(mid);
                    }
                    cursor.dispose();
                    cursor = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            FileLog.e(e);
        }
        if (kept.size() == 0) {
            return deletedMessages;
        }

        int now = getConnectionsManager().getCurrentTime();
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            for (int a = 0; a < kept.size(); a++) {
                long did = kept.keyAt(a);
                Set<Integer> set = ids(deleted, did);
                for (int mid : kept.valueAt(a)) {
                    ContentValues values = new ContentValues();
                    values.put("dialog_id", did);
                    values.put("mid", mid);
                    values.put("date", now);
                    database.insertWithOnConflict("deleted", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                    set.add(mid);
                }
            }
            database.setTransactionSuccessful();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            database.endTransaction();
        }

        LongSparseArray<ArrayList<Integer>> rest = new LongSparseArray<>();
        for (int a = 0, size = deletedMessages.size(); a < size; a++) {
            long key = deletedMessages.keyAt(a);
            Set<Integer> source = keptBySource.get(key);
            ArrayList<Integer> remaining = new ArrayList<>();
            for (Integer mid : deletedMessages.valueAt(a)) {
                if (source == null || !source.contains(mid)) {
                    remaining.add(mid);
                }
            }
            if (!remaining.isEmpty()) {
                rest.put(key, remaining);
            }
        }

        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < kept.size(); a++) {
                getNotificationCenter().postNotificationName(NotificationCenter.humoyMessagesMarked, kept.keyAt(a), kept.valueAt(a));
            }
        });
        return rest.size() == 0 ? null : rest;
    }

    /** Marks messages as deleted here, without removing them: they were deleted on the server from this phone. */
    public void markDeleted(long dialogId, ArrayList<Integer> mids) {
        if (dialogId == 0 || mids == null || mids.isEmpty()) {
            return;
        }
        ensureLoaded();
        ArrayList<Integer> list = new ArrayList<>(mids);
        Set<Integer> set = ids(deleted, dialogId);
        set.addAll(list);
        int now = getConnectionsManager().getCurrentTime();
        Utilities.globalQueue.postRunnable(() -> {
            synchronized (dbSync) {
                SQLiteDatabase database = db();
                database.beginTransaction();
                try {
                    for (int mid : list) {
                        ContentValues values = new ContentValues();
                        values.put("dialog_id", dialogId);
                        values.put("mid", mid);
                        values.put("date", now);
                        database.insertWithOnConflict("deleted", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                    database.setTransactionSuccessful();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    database.endTransaction();
                }
            }
        });
        getNotificationCenter().postNotificationName(NotificationCenter.humoyMessagesMarked, dialogId, list);
    }

    /**
     * Called while MessagesController processes an edit, before the new version is written. Runs on the
     * storage queue, so it reads the version Telegram has now and saves its text if the edit changes it.
     */
    public void onMessageEdited(TLRPC.Message newMessage) {
        if (!HumoyConfig.saveEditHistory || newMessage == null || newMessage.id <= 0) {
            return;
        }
        long dialogId = MessageObject.getDialogId(newMessage);
        String newText = newMessage.message == null ? "" : newMessage.message;
        String newMedia = mediaKey(newMessage);
        int newDate = newMessage.edit_date;
        MessagesStorage storage = getMessagesStorage();
        storage.getStorageQueue().postRunnable(() -> {
            TLRPC.Message old = null;
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", newMessage.id, dialogId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        old = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            if (old == null) {
                return;
            }
            String oldText = old.message == null ? "" : old.message;
            int oldDate = old.edit_date != 0 ? old.edit_date : old.date;
            boolean mediaChanged = !mediaKey(old).equals(newMedia);
            if (oldText.equals(newText) && !mediaChanged || oldDate > newDate && newDate != 0) {
                return;
            }
            if (mediaChanged) {
                backupMedia(old);
            }
            saveEditVersion(dialogId, newMessage.id, old, oldText, oldDate);
        });
    }

    /** Identifies the attached photo or file; link previews don't count, they change on their own. */
    private static String mediaKey(TLRPC.Message message) {
        TLRPC.MessageMedia media = message.media;
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) {
            return "";
        }
        if (media.photo != null) {
            return "p" + media.photo.id;
        }
        if (media.document != null) {
            return "d" + media.document.id;
        }
        return media.getClass().getSimpleName();
    }

    private File mediaBackupDir() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "humoy_media" + (currentAccount == 0 ? "" : currentAccount));
        dir.mkdirs();
        return dir;
    }

    /** Copies the replaced photo or video, if it was downloaded, so Telegram's cache cleanup can't take it. */
    private void backupMedia(TLRPC.Message old) {
        try {
            File file = getFileLoader().getPathToMessage(old);
            if (file != null && file.exists()) {
                File backup = new File(mediaBackupDir(), file.getName());
                if (!backup.exists()) {
                    AndroidUtilities.copyFile(file, backup);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Puts a backed up file back where Telegram looks for it, if Telegram's copy is gone. */
    public void restoreMedia(TLRPC.Message message) {
        try {
            File file = getFileLoader().getPathToMessage(message);
            if (file != null && !file.exists()) {
                File backup = new File(mediaBackupDir(), file.getName());
                if (backup.exists()) {
                    AndroidUtilities.copyFile(backup, file);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveEditVersion(long dialogId, int mid, TLRPC.Message old, String text, int date) {
        ensureLoaded();
        try {
            SQLiteDatabase database = db();
            byte[] bytes = null;
            try {
                SerializedData data = new SerializedData(old.getObjectSize());
                old.serializeToStream(data);
                bytes = data.toByteArray();
                data.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
            ContentValues values = new ContentValues();
            values.put("dialog_id", dialogId);
            values.put("mid", mid);
            values.put("date", date);
            values.put("text", text);
            values.put("data", bytes);
            database.insert("edits", null, values);
            ids(edited, dialogId).add(mid);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Earlier versions of a message, oldest first. */
    public ArrayList<EditVersion> getEditHistory(long dialogId, int mid) {
        ArrayList<EditVersion> result = new ArrayList<>();
        try (Cursor c = db().rawQuery("SELECT text, date, data FROM edits WHERE dialog_id = ? AND mid = ? ORDER BY date ASC, rowid ASC",
                new String[]{String.valueOf(dialogId), String.valueOf(mid)})) {
            while (c.moveToNext()) {
                TLRPC.Message message = null;
                byte[] bytes = c.isNull(2) ? null : c.getBlob(2);
                if (bytes != null) {
                    try {
                        SerializedData data = new SerializedData(bytes);
                        message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                result.add(new EditVersion(c.getString(0), c.getInt(1), message));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public int getDeletedCount(long dialogId) {
        ensureLoaded();
        Set<Integer> set = deleted.get(dialogId);
        return set == null ? 0 : set.size();
    }

    /** The kept deleted messages of a chat, oldest first, read from Telegram's cache; the callback runs on the UI thread. */
    public void loadDeletedMessages(long dialogId, Utilities.Callback<ArrayList<MessageObject>> callback) {
        ensureLoaded();
        Set<Integer> set = deleted.get(dialogId);
        ArrayList<Integer> mids = set == null ? new ArrayList<>() : new ArrayList<>(set);
        if (mids.isEmpty()) {
            callback.run(new ArrayList<>());
            return;
        }
        MessagesStorage storage = getMessagesStorage();
        long selfId = getUserConfig().getClientUserId();
        storage.getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.Message> messages = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM messages_v2 WHERE uid = %d AND mid IN(%s) ORDER BY mid ASC", dialogId, TextUtils.join(",", mids)));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, selfId);
                        message.id = cursor.intValue(1);
                        message.dialog_id = dialogId;
                        messages.add(message);
                    }
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<MessageObject> result = new ArrayList<>();
                for (TLRPC.Message message : messages) {
                    result.add(new MessageObject(currentAccount, message, true, true));
                }
                callback.run(result);
            });
        });
    }

    /** Removes a chat's kept deleted messages from this device for good, and from the open chat. */
    public void clearDeleted(long dialogId) {
        ensureLoaded();
        Set<Integer> set = deleted.get(dialogId);
        if (set == null || set.isEmpty()) {
            return;
        }
        ArrayList<Integer> mids = new ArrayList<>(set);
        long channelId = DialogObject.isChatDialog(dialogId) && ChatObject.isChannel(getMessagesController().getChat(-dialogId)) ? -dialogId : 0;
        // forgets our marks too (onMessagesRemoved), since it goes through Telegram's own deletion
        getMessagesStorage().markMessagesAsDeleted(dialogId, mids, true, true, 0, 0);
        getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, channelId, mids, null);
        getMessagesController().markDialogMessageAsDeleted(dialogId, mids);
        getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, channelId, false);
    }

    /** Messages removed from this device for real (you deleted them, or the chat was cleared): forget them. */
    public void onMessagesRemoved(long dialogId, ArrayList<Integer> mids) {
        if (mids == null || mids.isEmpty()) {
            return;
        }
        ensureLoaded();
        Set<Integer> del = deleted.get(dialogId);
        Set<Integer> ed = edited.get(dialogId);
        boolean hasDeleted = false, hasEdited = false;
        for (Integer mid : mids) {
            hasDeleted |= del != null && del.remove(mid);
            hasEdited |= ed != null && ed.remove(mid);
        }
        if (!hasDeleted && !hasEdited) {
            return;
        }
        String ids = TextUtils.join(",", mids);
        try {
            if (hasDeleted) {
                db().execSQL(String.format(Locale.US, "DELETE FROM deleted WHERE dialog_id = %d AND mid IN(%s)", dialogId, ids));
            }
            if (hasEdited) {
                db().execSQL(String.format(Locale.US, "DELETE FROM edits WHERE dialog_id = %d AND mid IN(%s)", dialogId, ids));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
