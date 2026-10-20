/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Humoy-only settings. Shared by all accounts.
 */
public class HumoyConfig {

    // Humoy's own version, shown to people; the Telegram version it is built on is BuildVars.BUILD_VERSION_STRING
    public static final String VERSION = "1.0.0";

    private static final Object sync = new Object();
    private static boolean configLoaded;

    // Ghost mode: the master switch, plus which parts of it are active.
    public static boolean ghostMode;
    public static boolean ghostDontReadMessages = true;
    public static boolean ghostDontReadStories = true;
    public static boolean ghostDontSendOnline = true;
    public static boolean ghostDontSendTyping = true;
    public static boolean ghostGoOffline = true;
    // Mark a chat as read when you reply or react in it, even with ghost mode on.
    public static boolean ghostReadOnInteract = true;
    // Show the ghost toggle in the main screen header.
    public static boolean showGhostButton = true;
    // Before opening someone's story with ghost mode off, ask whether to turn it on first.
    public static boolean askGhostBeforeStory = true;

    // Ask before sending a sticker or a GIF; voice messages open the listen-before-send panel instead of sending.
    public static boolean confirmSticker;
    public static boolean confirmVoice;
    public static boolean confirmGif;

    // Keep messages others delete (marked as deleted) and the earlier texts of edited messages, on this device only.
    public static boolean keepDeletedMessages = true;
    public static boolean saveEditHistory = true;
    // Show "Save to gallery" on every story, also where its author or Premium would not allow it.
    public static boolean saveAnyStory = true;
    // Keep who viewed your stories, to show them after Telegram stops listing them without Premium.
    public static boolean keepStoryViewers = true;
    public static boolean showMutualContacts = true;
    // Save, copy and forward messages from chats and channels that restrict saving content.
    public static boolean saveProtectedContent = true;
    // Self-destructing (view-once / timed) photos and videos: keep a copy on the device as soon as they arrive,
    // and open them like ordinary media, so the timer doesn't start and the sender isn't told they were opened.
    public static boolean saveSelfDestructMedia = true;
    public static boolean viewSelfDestructSilently = true;
    // When such media is opened, tell the sender it was viewed, so its timer runs out on their side as usual
    // (the copy here stays). Not sent while ghost mode hides read receipts.
    public static boolean autoMarkSelfDestructViewed = true;
    // Contact changes: whose (HumoyContactChanges.SCOPE_*), which kinds (bit mask of TYPE_*),
    // people with unseen changes on top, and whether the screen opens on people or on all changes
    public static int contactChangesScope = 0;
    public static int contactChangesTypes = (1 << 7) - 1;
    public static boolean contactChangesUnseenFirst = true;
    public static int contactChangesView = 0;

    public static final int GHOST_OPTIONS_COUNT = 5;

    static {
        loadConfig();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoyconfig", Context.MODE_PRIVATE);
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded || ApplicationLoader.applicationContext == null) {
                return;
            }
            SharedPreferences p = prefs();
            ghostMode = p.getBoolean("ghostMode", false);
            ghostDontReadMessages = p.getBoolean("ghostDontReadMessages", true);
            ghostDontReadStories = p.getBoolean("ghostDontReadStories", true);
            ghostDontSendOnline = p.getBoolean("ghostDontSendOnline", true);
            ghostDontSendTyping = p.getBoolean("ghostDontSendTyping", true);
            ghostGoOffline = p.getBoolean("ghostGoOffline", true);
            ghostReadOnInteract = p.getBoolean("ghostReadOnInteract", true);
            showGhostButton = p.getBoolean("showGhostButton", true);
            askGhostBeforeStory = p.getBoolean("askGhostBeforeStory", true);
            confirmSticker = p.getBoolean("confirmSticker", false);
            confirmVoice = p.getBoolean("confirmVoice", false);
            confirmGif = p.getBoolean("confirmGif", false);
            keepDeletedMessages = p.getBoolean("keepDeletedMessages", true);
            saveEditHistory = p.getBoolean("saveEditHistory", true);
            saveAnyStory = p.getBoolean("saveAnyStory", true);
            keepStoryViewers = p.getBoolean("keepStoryViewers", true);
            showMutualContacts = p.getBoolean("showMutualContacts", true);
            saveProtectedContent = p.getBoolean("saveProtectedContent", true);
            saveSelfDestructMedia = p.getBoolean("saveSelfDestructMedia", true);
            viewSelfDestructSilently = p.getBoolean("viewSelfDestructSilently", true);
            autoMarkSelfDestructViewed = p.getBoolean("autoMarkSelfDestructViewed", true);
            if (ApplicationLoader.isPlayStoreBuild()) {
                saveProtectedContent = false;
                saveSelfDestructMedia = false;
                viewSelfDestructSilently = false;
                autoMarkSelfDestructViewed = false;
            }
            contactChangesScope = p.getInt("contactChangesScope", 0);
            contactChangesTypes = p.getInt("contactChangesTypes", (1 << 7) - 1);
            contactChangesUnseenFirst = p.getBoolean("contactChangesUnseenFirst", true);
            contactChangesView = p.getInt("contactChangesView", 0);
            configLoaded = true;
        }
    }

    public static void saveConfig() {
        prefs().edit()
                .putBoolean("ghostMode", ghostMode)
                .putBoolean("ghostDontReadMessages", ghostDontReadMessages)
                .putBoolean("ghostDontReadStories", ghostDontReadStories)
                .putBoolean("ghostDontSendOnline", ghostDontSendOnline)
                .putBoolean("ghostDontSendTyping", ghostDontSendTyping)
                .putBoolean("ghostGoOffline", ghostGoOffline)
                .putBoolean("ghostReadOnInteract", ghostReadOnInteract)
                .putBoolean("showGhostButton", showGhostButton)
                .putBoolean("askGhostBeforeStory", askGhostBeforeStory)
                .putBoolean("confirmSticker", confirmSticker)
                .putBoolean("confirmVoice", confirmVoice)
                .putBoolean("confirmGif", confirmGif)
                .putBoolean("keepDeletedMessages", keepDeletedMessages)
                .putBoolean("saveEditHistory", saveEditHistory)
                .putBoolean("saveAnyStory", saveAnyStory)
                .putBoolean("keepStoryViewers", keepStoryViewers)
                .putBoolean("showMutualContacts", showMutualContacts)
                .putBoolean("saveProtectedContent", saveProtectedContent)
                .putBoolean("saveSelfDestructMedia", saveSelfDestructMedia)
                .putBoolean("viewSelfDestructSilently", viewSelfDestructSilently)
                .putBoolean("autoMarkSelfDestructViewed", autoMarkSelfDestructViewed)
                .putInt("contactChangesScope", contactChangesScope)
                .putInt("contactChangesTypes", contactChangesTypes)
                .putBoolean("contactChangesUnseenFirst", contactChangesUnseenFirst)
                .putInt("contactChangesView", contactChangesView)
                .apply();
    }

    // people picked by hand for contact changes ("custom contacts")
    private static java.util.Set<Long> watchedContacts;

    public static synchronized boolean isContactChangesWatched(long userId) {
        return getWatchedContacts().contains(userId);
    }

    public static synchronized java.util.Set<Long> getWatchedContacts() {
        if (watchedContacts == null) {
            watchedContacts = new java.util.HashSet<>();
            for (String s : prefs().getStringSet("contactChangesWatched", java.util.Collections.emptySet())) {
                try {
                    watchedContacts.add(Long.parseLong(s));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return watchedContacts;
    }

    public static synchronized void setWatchedContacts(java.util.Collection<Long> ids) {
        watchedContacts = new java.util.HashSet<>(ids);
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (Long id : ids) {
            set.add(String.valueOf(id));
        }
        prefs().edit().putStringSet("contactChangesWatched", set).apply();
    }

    public static void toggleGhostMode() {
        setGhostMode(!ghostMode);
    }

    public static void setGhostMode(boolean enabled) {
        ghostMode = enabled;
        applyGhostOptions();
    }

    /** Saves ghost settings and, if read receipts are no longer hidden, sends the ones held back. */
    public static void applyGhostOptions() {
        saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.humoyGhostModeChanged);
        if (!hideReadMessages()) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    MessagesController.getInstance(a).flushGhostReads();
                }
            }
        }
    }

    public static int ghostOptionsEnabledCount() {
        return (ghostDontReadMessages ? 1 : 0) + (ghostDontReadStories ? 1 : 0) + (ghostDontSendOnline ? 1 : 0)
                + (ghostDontSendTyping ? 1 : 0) + (ghostGoOffline ? 1 : 0);
    }

    public static boolean hideReadMessages() {
        return ghostMode && ghostDontReadMessages;
    }

    // Per-chat exceptions to ghost mode, chosen from the chat's menu. Kept on the device for all accounts.
    public static final int EXCLUSION_DEFAULT = 0;
    public static final int EXCLUSION_NEVER = 1;
    public static final int EXCLUSION_ALWAYS = 2;

    public static int getReadExclusion(long dialogId) {
        return dialogId == 0 ? EXCLUSION_DEFAULT : prefs().getInt("readExcl" + dialogId, EXCLUSION_DEFAULT);
    }

    public static int getTypingExclusion(long dialogId) {
        return dialogId == 0 ? EXCLUSION_DEFAULT : prefs().getInt("typingExcl" + dialogId, EXCLUSION_DEFAULT);
    }

    public static void setReadExclusion(long dialogId, int mode) {
        prefs().edit().putInt("readExcl" + dialogId, mode).apply();
        if (!hideReadMessages(dialogId)) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    MessagesController.getInstance(a).flushGhostReads();
                }
            }
        }
    }

    public static void setTypingExclusion(long dialogId, int mode) {
        prefs().edit().putInt("typingExcl" + dialogId, mode).apply();
    }

    /** Whether read receipts for this chat are held back: its own choice, else ghost mode's. */
    public static boolean hideReadMessages(long dialogId) {
        int mode = getReadExclusion(dialogId);
        return mode == EXCLUSION_NEVER || mode != EXCLUSION_ALWAYS && hideReadMessages();
    }

    public static boolean hideTyping(long dialogId) {
        int mode = getTypingExclusion(dialogId);
        return mode == EXCLUSION_NEVER || mode != EXCLUSION_ALWAYS && hideTyping();
    }

    private static long dialogIdOf(TLRPC.InputPeer peer) {
        return peer == null ? 0 : DialogObject.getPeerDialogId(peer);
    }

    private static long dialogIdOf(TLRPC.InputChannel channel) {
        return channel == null ? 0 : -channel.channel_id;
    }

    /** The chat a read or typing request is about, or 0 if the request doesn't say. */
    private static long requestDialogId(TLObject object) {
        if (object instanceof TLRPC.TL_messages_readHistory) {
            return dialogIdOf(((TLRPC.TL_messages_readHistory) object).peer);
        } else if (object instanceof TLRPC.TL_channels_readHistory) {
            return dialogIdOf(((TLRPC.TL_channels_readHistory) object).channel);
        } else if (object instanceof TLRPC.TL_messages_readDiscussion) {
            return dialogIdOf(((TLRPC.TL_messages_readDiscussion) object).peer);
        } else if (object instanceof TLRPC.TL_messages_readSavedHistory) {
            return dialogIdOf(((TLRPC.TL_messages_readSavedHistory) object).parent_peer);
        } else if (object instanceof TLRPC.TL_channels_readMessageContents) {
            return dialogIdOf(((TLRPC.TL_channels_readMessageContents) object).channel);
        } else if (object instanceof TLRPC.TL_messages_setTyping) {
            return dialogIdOf(((TLRPC.TL_messages_setTyping) object).peer);
        }
        return 0;
    }

    public static boolean hideReadStories() {
        return ghostMode && ghostDontReadStories;
    }

    public static boolean hideOnline() {
        return ghostMode && ghostDontSendOnline;
    }

    public static boolean hideTyping() {
        return ghostMode && ghostDontSendTyping;
    }

    public static boolean forceOffline() {
        return ghostMode && ghostGoOffline;
    }

    // Requests Humoy sends on purpose while ghost mode is on (e.g. "read on interact"); the filter lets them through.
    private static final Set<TLObject> allowedRequests = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    public static void allowRequest(TLObject request) {
        allowedRequests.add(request);
    }

    /**
     * Last line of defence, run on every outgoing request: whatever code path built it,
     * nothing ghost mode hides leaves the device. Returns true if the request must be dropped.
     */
    public static boolean filterRequest(TLObject object) {
        if (allowedRequests.remove(object)) {
            return false;
        }
        // per-chat exceptions apply even with ghost mode off
        if (object instanceof TLRPC.TL_messages_setTyping || object instanceof TLRPC.TL_messages_setEncryptedTyping) {
            return hideTyping(requestDialogId(object));
        }
        if (isReadRequest(object)) {
            return hideReadMessages(requestDialogId(object));
        }
        if (!ghostMode) {
            return false;
        }
        if (object instanceof TL_account.updateStatus) {
            if (hideOnline()) {
                ((TL_account.updateStatus) object).offline = true;
            }
            return false;
        }
        if (object instanceof TL_stories.TL_stories_readStories || object instanceof TL_stories.TL_stories_incrementStoryViews) {
            return hideReadStories();
        }
        return false;
    }

    public static boolean isReadRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory
                || object instanceof TLRPC.TL_channels_readHistory
                || object instanceof TLRPC.TL_messages_readEncryptedHistory
                || object instanceof TLRPC.TL_messages_readDiscussion
                || object instanceof TLRPC.TL_messages_readSavedHistory
                || object instanceof TLRPC.TL_messages_readMessageContents
                || object instanceof TLRPC.TL_channels_readMessageContents;
    }
}
