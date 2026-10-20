/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.messenger.secretmedia.EncryptedFileInputStream;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-destructing photos and videos (view-once and timed, in private and secret chats).
 * <p>
 * As soon as such a message reaches the device its file is downloaded and copied into Humoy's own
 * folder, before anyone opens it, so the copy survives the timer, the sender deleting it and cache
 * cleanup. With {@link HumoyConfig#viewSelfDestructSilently} the media is also shown and opened like
 * ordinary media: no "read" is sent, so the timer never starts and the sender isn't told.
 * Nothing here is ever sent anywhere.
 */
public class HumoyTtlMedia extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    public static final int TYPE_PHOTO = 0;
    public static final int TYPE_VIDEO = 1;

    public static class Entry {
        public long dialogId;
        public int mid;
        public long fromId;
        public int date;
        public int ttl;
        public int type;
        public String path;
        public int savedDate;

        public File getFile() {
            return new File(path);
        }

        public boolean isVideo() {
            return type == TYPE_VIDEO;
        }
    }

    private static final HumoyTtlMedia[] Instance = new HumoyTtlMedia[UserConfig.MAX_ACCOUNT_COUNT];

    public static HumoyTtlMedia getInstance(int num) {
        HumoyTtlMedia localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (HumoyTtlMedia.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new HumoyTtlMedia(num);
                }
            }
        }
        return localInstance;
    }

    private SQLiteDatabase db;
    private final Object dbSync = new Object();
    private volatile boolean loaded;
    // "dialogId_mid" of everything saved, to skip it quickly
    private final Set<String> saved = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // UI thread only: file name being downloaded -> messages waiting for it
    private final HashMap<String, ArrayList<TLRPC.Message>> waiting = new HashMap<>();
    private final Set<String> pending = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean observing;

    private HumoyTtlMedia(int num) {
        super(num);
    }

    // ---- what counts as self-destructing media

    /** A view-once or timed photo or video (not voice, round video or paid media). */
    public static boolean isSelfDestructMedia(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media == null || media instanceof TLRPC.TL_messageMediaPaidMedia) {
            return false;
        }
        int ttl = message instanceof TLRPC.TL_message_secret ? Math.max(message.ttl, media.ttl_seconds) : media.ttl_seconds;
        if (ttl == 0) {
            return false;
        }
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return media.photo != null && !(media.photo instanceof TLRPC.TL_photoEmpty) && !media.photo.sizes.isEmpty();
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = media.document;
            return document != null && !(document instanceof TLRPC.TL_documentEmpty)
                    && !MessageObject.isRoundVideoDocument(document) && !MessageObject.isVoiceDocument(document)
                    && (MessageObject.isVideoDocument(document) || MessageObject.isGifDocument(document));
        }
        return false;
    }

    /** Whether this media is shown and opened like ordinary media, without starting its timer. */
    public static boolean viewsSilently(TLRPC.Message message) {
        return HumoyConfig.viewSelfDestructSilently && isSelfDestructMedia(message);
    }

    public static boolean viewsSilently(MessageObject messageObject) {
        return messageObject != null && !messageObject.hasExtendedMediaPreview() && viewsSilently(messageObject.messageOwner);
    }

    /** Incoming self-destructing media that hasn't been opened (its timer hasn't started). */
    public static boolean canMarkViewed(MessageObject messageObject) {
        return viewsSilently(messageObject) && !messageObject.isOut() && messageObject.getId() != 0
                && messageObject.messageOwner.destroyTime == 0 && !messageObject.forceExpired && messageObject.messageOwner.ttl > 0
                && messageObject.messageOwner.media_unread;
    }

    /** Short timer text for the bubble: 30s, 5m, 1h. */
    public static String shortTtl(int ttl) {
        if (ttl < 60) {
            return ttl + "s";
        } else if (ttl < 3600) {
            return (ttl / 60) + "m";
        } else if (ttl < 86400) {
            return (ttl / 3600) + "h";
        }
        return (ttl / 86400) + "d";
    }

    public static int ttlOf(TLRPC.Message message) {
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        int mediaTtl = media != null ? media.ttl_seconds : 0;
        return message instanceof TLRPC.TL_message_secret ? Math.max(message.ttl, mediaTtl) : (mediaTtl != 0 ? mediaTtl : message.ttl);
    }

    // "dialogId_mid" of media whose "viewed" was already sent automatically in this session
    private final Set<String> autoMarked = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Whether opening this media should now tell its sender it was viewed. Returns true once per
     * message: incoming, not viewed yet, and read receipts for the chat not hidden by ghost mode.
     */
    public boolean takeAutoMarkViewed(MessageObject messageObject) {
        if (!HumoyConfig.autoMarkSelfDestructViewed || !viewsSilently(messageObject) || messageObject.isOut()
                || !messageObject.messageOwner.media_unread || messageObject.messageOwner.destroyTime != 0 || messageObject.forceExpired
                || HumoyConfig.hideReadMessages(messageObject.getDialogId())) {
            return false;
        }
        return autoMarked.add(key(messageObject.getDialogId(), messageObject.getId()));
    }

    // ---- storage

    private SQLiteDatabase db() {
        synchronized (dbSync) {
            if (db == null) {
                File dir = ApplicationLoader.getFilesDirFixed();
                String name = currentAccount == 0 ? "humoy_ttl.db" : "humoy_ttl" + currentAccount + ".db";
                db = SQLiteDatabase.openOrCreateDatabase(new File(dir, name), null);
                db.execSQL("CREATE TABLE IF NOT EXISTS media(dialog_id INTEGER, mid INTEGER, from_id INTEGER, date INTEGER, ttl INTEGER, type INTEGER, path TEXT, saved INTEGER, data BLOB, PRIMARY KEY(dialog_id, mid))");
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
            try (Cursor c = db().rawQuery("SELECT dialog_id, mid FROM media", null)) {
                while (c.moveToNext()) {
                    saved.add(key(c.getLong(0), c.getInt(1)));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            loaded = true;
        }
    }

    public static File getDirectory(int account) {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "humoy_ttl" + (account == 0 ? "" : String.valueOf(account)));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            new File(dir, ".nomedia").createNewFile();
        } catch (Exception ignore) {
        }
        return dir;
    }

    private static String key(long dialogId, int mid) {
        return dialogId + "_" + mid;
    }

    private static long dialogIdOf(TLRPC.Message message) {
        return message.dialog_id != 0 ? message.dialog_id : MessageObject.getDialogId(message);
    }

    public boolean isSaved(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        ensureLoaded();
        return saved.contains(key(messageObject.getDialogId(), messageObject.getId()));
    }

    /** Saved copies, newest first. Reads the database: call off the UI thread when possible. */
    public ArrayList<Entry> getEntries() {
        ArrayList<Entry> result = new ArrayList<>();
        synchronized (dbSync) {
            try (Cursor c = db().rawQuery("SELECT dialog_id, mid, from_id, date, ttl, type, path, saved FROM media ORDER BY saved DESC, date DESC", null)) {
                while (c.moveToNext()) {
                    Entry entry = new Entry();
                    entry.dialogId = c.getLong(0);
                    entry.mid = c.getInt(1);
                    entry.fromId = c.getLong(2);
                    entry.date = c.getInt(3);
                    entry.ttl = c.getInt(4);
                    entry.type = c.getInt(5);
                    entry.path = c.getString(6);
                    entry.savedDate = c.getInt(7);
                    if (entry.getFile().exists()) {
                        result.add(entry);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return result;
    }

    public void delete(Entry entry) {
        synchronized (dbSync) {
            try {
                db().delete("media", "dialog_id = ? AND mid = ?", new String[]{String.valueOf(entry.dialogId), String.valueOf(entry.mid)});
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        saved.remove(key(entry.dialogId, entry.mid));
        entry.getFile().delete();
    }

    public void deleteAll() {
        for (Entry entry : getEntries()) {
            delete(entry);
        }
        synchronized (dbSync) {
            try {
                db().delete("media", null, null);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        saved.clear();
    }

    // ---- saving

    /** New messages shown in the interface (UI thread). */
    public void onMessageObjects(ArrayList<MessageObject> messages) {
        if (!HumoyConfig.saveSelfDestructMedia || messages == null) {
            return;
        }
        for (int a = 0; a < messages.size(); a++) {
            MessageObject messageObject = messages.get(a);
            if (messageObject != null && !messageObject.scheduled) {
                save(messageObject.messageOwner);
            }
        }
    }

    /** Messages loaded for a chat, from the cache or the server (any thread). */
    public void onMessages(ArrayList<TLRPC.Message> messages) {
        if (!HumoyConfig.saveSelfDestructMedia || messages == null || messages.isEmpty()) {
            return;
        }
        ArrayList<TLRPC.Message> found = null;
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message != null && !message.out && isSelfDestructMedia(message)) {
                if (found == null) {
                    found = new ArrayList<>();
                }
                found.add(message);
            }
        }
        if (found != null) {
            ArrayList<TLRPC.Message> list = found;
            AndroidUtilities.runOnUIThread(() -> {
                for (int a = 0; a < list.size(); a++) {
                    save(list.get(a));
                }
            });
        }
    }

    private void save(TLRPC.Message message) {
        if (message == null || message.out || message.id == 0 || !isSelfDestructMedia(message)) {
            return;
        }
        long dialogId = dialogIdOf(message);
        if (dialogId == 0) {
            return;
        }
        String key = key(dialogId, message.id);
        ensureLoaded();
        if (saved.contains(key) || !pending.add(key)) {
            return;
        }
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        TLObject attach;
        String fileName;
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
            if (size == null) {
                pending.remove(key);
                return;
            }
            attach = size;
        } else {
            attach = media.document;
        }
        fileName = FileLoader.getAttachFileName(attach);
        if (fileName == null || fileName.isEmpty()) {
            pending.remove(key);
            return;
        }

        File existing = findDownloaded(attach);
        if (existing != null) {
            copy(message, existing);
            return;
        }

        ArrayList<TLRPC.Message> list = waiting.get(fileName);
        if (list == null) {
            waiting.put(fileName, list = new ArrayList<>());
        }
        list.add(message);
        if (!observing) {
            observing = true;
            getNotificationCenter().addObserver(this, NotificationCenter.fileLoaded);
            getNotificationCenter().addObserver(this, NotificationCenter.fileLoadFailed);
        }
        MessageObject parent = new MessageObject(currentAccount, message, false, false);
        // the same storage the chat uses for it (encrypted when Telegram's own viewer opens it), so the two don't clash
        int cacheType = MessageObject.shouldEncryptPhotoOrVideo(currentAccount, message) ? 2 : 1;
        if (attach instanceof TLRPC.PhotoSize) {
            getFileLoader().loadFile(ImageLocation.getForPhoto((TLRPC.PhotoSize) attach, media.photo), parent, "jpg", FileLoader.PRIORITY_NORMAL, cacheType);
        } else {
            getFileLoader().loadFile((TLRPC.Document) attach, parent, FileLoader.PRIORITY_NORMAL, cacheType);
        }
    }

    private File findDownloaded(TLObject attach) {
        File[] candidates = new File[]{
                getFileLoader().getPathToAttach(attach, true),
                getFileLoader().getPathToAttach(attach, false)
        };
        for (File file : candidates) {
            if (file != null && file.exists() && file.length() > 0) {
                return file;
            }
            if (file != null) {
                File encrypted = new File(file.getAbsolutePath() + ".enc");
                if (encrypted.exists() && encrypted.length() > 0) {
                    return encrypted;
                }
            }
        }
        return null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            String fileName = (String) args[0];
            ArrayList<TLRPC.Message> list = waiting.remove(fileName);
            if (list != null) {
                File file = (File) args[1];
                for (int a = 0; a < list.size(); a++) {
                    copy(list.get(a), file);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String fileName = (String) args[0];
            ArrayList<TLRPC.Message> list = waiting.remove(fileName);
            if (list != null) {
                for (int a = 0; a < list.size(); a++) {
                    pending.remove(key(dialogIdOf(list.get(a)), list.get(a).id));
                }
            }
        }
        if (waiting.isEmpty() && observing) {
            observing = false;
            getNotificationCenter().removeObserver(this, NotificationCenter.fileLoaded);
            getNotificationCenter().removeObserver(this, NotificationCenter.fileLoadFailed);
        }
    }

    private void copy(TLRPC.Message message, File source) {
        Utilities.globalQueue.postRunnable(() -> {
            long dialogId = dialogIdOf(message);
            String key = key(dialogId, message.id);
            boolean photo = MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto;
            String ext = photo ? "jpg" : extensionOf(message);
            File dest = new File(getDirectory(currentAccount), (dialogId < 0 ? "m" + (-dialogId) : String.valueOf(dialogId)) + "_" + message.id + "." + ext);
            boolean ok = false;
            try {
                if (source == null || !source.exists()) {
                    ok = false;
                } else if (source.getName().endsWith(".enc")) {
                    File keyFile = new File(FileLoader.getInternalCacheDir(), source.getName() + ".key");
                    try (InputStream in = new EncryptedFileInputStream(source, keyFile)) {
                        ok = AndroidUtilities.copyFile(in, dest);
                    }
                } else {
                    try (InputStream in = new FileInputStream(source)) {
                        ok = AndroidUtilities.copyFile(in, dest);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                ok = false;
            }
            if (!ok || !dest.exists() || dest.length() == 0) {
                dest.delete();
                pending.remove(key);
                return;
            }
            TLRPC.MessageMedia media = MessageObject.getMedia(message);
            int ttl = message instanceof TLRPC.TL_message_secret ? Math.max(message.ttl, media.ttl_seconds) : media.ttl_seconds;
            synchronized (dbSync) {
                try {
                    byte[] data = null;
                    try {
                        SerializedData serialized = new SerializedData(message.getObjectSize());
                        message.serializeToStream(serialized);
                        data = serialized.toByteArray();
                        serialized.cleanup();
                    } catch (Throwable ignore) {
                        // the file is what matters
                    }
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put("dialog_id", dialogId);
                    values.put("mid", message.id);
                    values.put("from_id", MessageObject.getFromChatId(message));
                    values.put("date", message.date);
                    values.put("ttl", ttl);
                    values.put("type", photo ? TYPE_PHOTO : TYPE_VIDEO);
                    values.put("path", dest.getAbsolutePath());
                    values.put("saved", (int) (System.currentTimeMillis() / 1000));
                    if (data != null) {
                        values.put("data", data);
                    }
                    db().insertWithOnConflict("media", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            saved.add(key);
            pending.remove(key);
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.humoySelfDestructMediaSaved, dialogId, message.id));
        });
    }

    private static String extensionOf(TLRPC.Message message) {
        TLRPC.Document document = MessageObject.getDocument(message);
        if (document != null && document.mime_type != null) {
            String mime = document.mime_type.toLowerCase();
            if (mime.endsWith("/webm")) {
                return "webm";
            } else if (mime.endsWith("/quicktime")) {
                return "mov";
            } else if (mime.equals("image/gif")) {
                return "gif";
            }
        }
        return "mp4";
    }
}
