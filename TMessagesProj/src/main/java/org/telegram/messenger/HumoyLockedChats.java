/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Humoy.HumoyPinActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Chats that open only after the locked chats PIN (or a fingerprint), every time they are opened.
 * Kept per account by its user id.
 */
public class HumoyLockedChats {

    private static final HashSet<String> unlocked = new HashSet<>();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoylocked", Context.MODE_PRIVATE);
    }

    private static String key(int account) {
        return "ids" + UserConfig.getInstance(account).getClientUserId();
    }

    private static Set<String> ids(int account) {
        return new HashSet<>(prefs().getStringSet(key(account), Collections.emptySet()));
    }

    public static boolean isLocked(int account, long dialogId) {
        return dialogId != 0 && HumoyPin.hasPin(HumoyPin.SCOPE_LOCKED) && ids(account).contains(String.valueOf(dialogId));
    }

    public static void setLocked(int account, long dialogId, boolean lock) {
        Set<String> set = ids(account);
        if (lock) {
            set.add(String.valueOf(dialogId));
        } else {
            set.remove(String.valueOf(dialogId));
        }
        prefs().edit().putStringSet(key(account), set).apply();
    }

    /** Turns the feature off: every chat opens freely and the PIN is forgotten. */
    public static void clearAll() {
        prefs().edit().clear().apply();
        unlocked.clear();
        HumoyPin.clearPin(HumoyPin.SCOPE_LOCKED);
    }

    /** The app went to the background: the chats ask for the PIN again. */
    public static void relock() {
        unlocked.clear();
    }

    public static long dialogIdOf(Bundle args) {
        if (args == null) {
            return 0;
        }
        long userId = args.getLong("user_id", 0);
        long chatId = args.getLong("chat_id", 0);
        int encId = args.getInt("enc_id", 0);
        if (encId != 0) {
            return DialogObject.makeEncryptedDialogId(encId);
        } else if (userId != 0) {
            return userId;
        } else if (chatId != 0) {
            return -chatId;
        }
        return 0;
    }

    /**
     * Called before a fragment is shown: a locked chat is replaced by the PIN screen, which opens the
     * chat when the PIN is right. Returns true when the original opening must not go on.
     */
    public static boolean intercept(INavigationLayout layout, INavigationLayout.NavigationParams params) {
        if (!(params.fragment instanceof ChatActivity)) {
            return false;
        }
        BaseFragment chat = params.fragment;
        int account = chat.getCurrentAccount();
        long dialogId = dialogIdOf(chat.getArguments());
        if (!isLocked(account, dialogId) || unlocked.remove(account + ":" + dialogId)) {
            // the PIN was just given for this opening; the next one asks again
            return false;
        }
        if (params.preview) {
            return true;
        }
        layout.presentFragment(new INavigationLayout.NavigationParams(new HumoyPinActivity(HumoyPin.SCOPE_LOCKED, HumoyPinActivity.MODE_CHECK,
                () -> unlocked.add(account + ":" + dialogId)).setNext(chat)));
        return true;
    }
}
