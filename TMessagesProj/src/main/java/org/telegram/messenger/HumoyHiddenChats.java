/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chats and contacts moved to the hidden section: they leave the chat lists, search and contacts and
 * show only in the hidden section, opened by long-pressing the new message button and typing the PIN.
 * Kept per account by its user id.
 */
public class HumoyHiddenChats {

    private static final SparseArray<HashSet<Long>> cache = new SparseArray<>();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoyhidden", Context.MODE_PRIVATE);
    }

    private static String key(int account) {
        return "ids" + UserConfig.getInstance(account).getClientUserId();
    }

    private static synchronized HashSet<Long> ids(int account) {
        HashSet<Long> set = cache.get(account);
        if (set == null) {
            set = new HashSet<>();
            for (String s : prefs().getStringSet(key(account), Collections.emptySet())) {
                try {
                    set.add(Long.parseLong(s));
                } catch (NumberFormatException ignore) {
                }
            }
            cache.put(account, set);
        }
        return set;
    }

    public static synchronized boolean isHidden(int account, long dialogId) {
        return HumoyPin.hasPin(HumoyPin.SCOPE_HIDDEN) && ids(account).contains(dialogId);
    }

    public static synchronized boolean hasHidden(int account) {
        return HumoyPin.hasPin(HumoyPin.SCOPE_HIDDEN) && !ids(account).isEmpty();
    }

    public static void setHidden(int account, List<Long> dialogIds, boolean hide) {
        synchronized (HumoyHiddenChats.class) {
            HashSet<Long> set = ids(account);
            if (hide) {
                set.addAll(dialogIds);
            } else {
                set.removeAll(dialogIds);
            }
            Set<String> strings = new HashSet<>();
            for (long id : set) {
                strings.add(String.valueOf(id));
            }
            prefs().edit().putStringSet(key(account), strings).apply();
        }
        changed(account);
    }

    /** Turns the feature off: everything hidden comes back and the PIN is forgotten. */
    public static void clearAll() {
        synchronized (HumoyHiddenChats.class) {
            prefs().edit().clear().apply();
            cache.clear();
        }
        HumoyPin.clearPin(HumoyPin.SCOPE_HIDDEN);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                changed(a);
            }
        }
    }

    public static boolean showNotifications() {
        return prefs().getBoolean("notify", true);
    }

    public static void setShowNotifications(boolean show) {
        prefs().edit().putBoolean("notify", show).apply();
    }

    /** Whether a new message in this chat should make no notification. */
    public static boolean muteNotification(int account, long dialogId) {
        return !showNotifications() && isHidden(account, dialogId);
    }

    /** The chats a list shows: without the hidden ones, or (in the hidden section) only them. */
    public static ArrayList<TLRPC.Dialog> filter(int account, ArrayList<TLRPC.Dialog> dialogs, boolean hiddenSection) {
        if (dialogs == null || !hiddenSection && !hasHidden(account)) {
            return dialogs;
        }
        HashSet<Long> set;
        synchronized (HumoyHiddenChats.class) {
            set = new HashSet<>(ids(account));
        }
        ArrayList<TLRPC.Dialog> result = new ArrayList<>(dialogs.size());
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog d = dialogs.get(i);
            if (d instanceof TLRPC.TL_dialogFolder) {
                if (!hiddenSection) {
                    result.add(d);
                }
                continue;
            }
            boolean hidden = d != null && set.contains(d.id);
            if (hidden == hiddenSection) {
                result.add(d);
            }
        }
        return result;
    }

    private static void changed(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            ContactsController.getInstance(account).humoyRebuildSections();
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        });
    }
}
