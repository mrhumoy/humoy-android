/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Keeps who viewed your stories. Telegram shows the full viewer list for free only until a day after a
 * story expires; Humoy writes the list down whenever it is loaded, and fetches it by itself every few
 * minutes while that window is open, so it can still be shown afterwards. Kept on this phone only.
 */
public class HumoyStoryViews extends BaseController {

    // the full list is free until expire_date + this (Telegram's StoriesUtilities.hasExpiredViews)
    private static final int FREE_WINDOW = 86400;
    private static final long REFRESH_EVERY = 10 * 60 * 1000L;

    private static final HumoyStoryViews[] Instance = new HumoyStoryViews[UserConfig.MAX_ACCOUNT_COUNT];

    public static HumoyStoryViews getInstance(int num) {
        HumoyStoryViews localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (HumoyStoryViews.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new HumoyStoryViews(num);
                }
            }
        }
        return localInstance;
    }

    private SQLiteDatabase db;
    private final Object dbSync = new Object();
    private long lastRefresh;
    private boolean refreshing;

    private HumoyStoryViews(int num) {
        super(num);
    }

    private SQLiteDatabase db() {
        synchronized (dbSync) {
            if (db == null) {
                File dir = ApplicationLoader.getFilesDirFixed();
                String name = currentAccount == 0 ? "humoy_story_views.db" : "humoy_story_views" + currentAccount + ".db";
                db = SQLiteDatabase.openOrCreateDatabase(new File(dir, name), null);
                db.execSQL("CREATE TABLE IF NOT EXISTS views(story_id INTEGER, peer_id INTEGER, date INTEGER, data BLOB, PRIMARY KEY(story_id, peer_id))");
            }
            return db;
        }
    }

    private static long peerOf(TL_stories.StoryView view) {
        if (view.user_id != 0) {
            return view.user_id;
        }
        if (view.peer_id != null) {
            return DialogObject.getPeerDialogId(view.peer_id);
        }
        if (view.message != null) {
            return MessageObject.getDialogId(view.message);
        }
        return 0;
    }

    /** Writes down viewers of one of your stories (any thread). */
    public void save(int storyId, ArrayList<TL_stories.StoryView> views) {
        if (views == null || views.isEmpty()) {
            return;
        }
        ArrayList<TL_stories.StoryView> copy = new ArrayList<>(views);
        Utilities.globalQueue.postRunnable(() -> {
            synchronized (dbSync) {
                SQLiteDatabase database = db();
                database.beginTransaction();
                try {
                    for (TL_stories.StoryView view : copy) {
                        long peer = peerOf(view);
                        if (peer == 0 || view.date == 0) {
                            // the placeholders Telegram builds from recent viewers have no date
                            continue;
                        }
                        SerializedData data = new SerializedData(view.getObjectSize());
                        view.serializeToStream(data);
                        ContentValues values = new ContentValues();
                        values.put("story_id", storyId);
                        values.put("peer_id", peer);
                        values.put("date", view.date);
                        values.put("data", data.toByteArray());
                        data.cleanup();
                        database.insertWithOnConflict("views", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                    database.setTransactionSuccessful();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    database.endTransaction();
                }
            }
        });
    }

    /** Saved viewers of a story, newest first (reads the database: small, fine on the UI thread). */
    public ArrayList<TL_stories.StoryView> load(int storyId) {
        ArrayList<TL_stories.StoryView> result = new ArrayList<>();
        synchronized (dbSync) {
            try (Cursor c = db().rawQuery("SELECT data FROM views WHERE story_id = " + storyId + " ORDER BY date DESC", null)) {
                while (c.moveToNext()) {
                    byte[] bytes = c.getBlob(0);
                    SerializedData data = new SerializedData(bytes);
                    TL_stories.StoryView view = TL_stories.StoryView.TLdeserialize(data, data.readInt32(false), false);
                    data.cleanup();
                    if (view != null) {
                        result.add(view);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return result;
    }

    public boolean has(int storyId) {
        synchronized (dbSync) {
            try (Cursor c = db().rawQuery("SELECT 1 FROM views WHERE story_id = " + storyId + " LIMIT 1", null)) {
                return c.moveToNext();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    /**
     * Fetches the viewers of your stories whose full list is still free, at most every 10 minutes.
     * Called when the app comes to the front and by a timer while it stays there.
     */
    public void refresh() {
        if (!HumoyConfig.keepStoryViewers || refreshing || !getUserConfig().isClientActivated()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_EVERY) {
            return;
        }
        lastRefresh = now;
        refreshing = true;
        TL_stories.TL_stories_getStoriesArchive req = new TL_stories.TL_stories_getStoriesArchive();
        req.peer = getMessagesController().getInputPeer(getUserConfig().getClientUserId());
        req.offset_id = 0;
        req.limit = 30;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TL_stories.TL_stories_stories)) {
                refreshing = false;
                return;
            }
            int serverNow = getConnectionsManager().getCurrentTime();
            ArrayList<TL_stories.StoryItem> open = new ArrayList<>();
            for (TL_stories.StoryItem story : ((TL_stories.TL_stories_stories) response).stories) {
                if (story != null && story.expire_date + FREE_WINDOW > serverNow) {
                    open.add(story);
                }
            }
            fetchNext(open, 0, "");
        }));
    }

    private void fetchNext(ArrayList<TL_stories.StoryItem> stories, int index, String offset) {
        if (index >= stories.size()) {
            refreshing = false;
            return;
        }
        TL_stories.StoryItem story = stories.get(index);
        TL_stories.TL_stories_getStoryViewsList req = new TL_stories.TL_stories_getStoryViewsList();
        req.peer = getMessagesController().getInputPeer(getUserConfig().getClientUserId());
        req.id = story.id;
        req.q = "";
        req.offset = offset;
        req.limit = 100;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TL_stories.StoryViewsList) {
                TL_stories.StoryViewsList res = (TL_stories.StoryViewsList) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                save(story.id, res.views);
                if (!res.views.isEmpty() && res.next_offset != null && !res.next_offset.isEmpty()) {
                    fetchNext(stories, index, res.next_offset);
                    return;
                }
            }
            fetchNext(stories, index + 1, "");
        }));
    }

    // a timer that keeps refreshing while the app is in front
    private static boolean timerStarted;

    public static void startTimer() {
        if (timerStarted) {
            return;
        }
        timerStarted = true;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (!ApplicationLoader.mainInterfacePaused) {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        if (UserConfig.getInstance(a).isClientActivated()) {
                            getInstance(a).refresh();
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(this, REFRESH_EVERY);
            }
        }, 15_000);
    }
}
