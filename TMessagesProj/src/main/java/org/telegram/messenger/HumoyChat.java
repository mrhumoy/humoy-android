/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The verified "Humoy" chat in the chat list: messages sent from Humoy's admin panel, kept on the
 * phone with the rest of the server's answer. Read only. What was read or deleted is kept per account.
 */
public class HumoyChat {

    private static String user(int account) {
        return String.valueOf(UserConfig.getInstance(account).getClientUserId());
    }

    private static Set<String> deleted(int account) {
        return HumoyServer.prefs().getStringSet("chat_deleted_" + user(account), Collections.emptySet());
    }

    public static JSONArray messages(int account) {
        JSONArray list = HumoyServer.state().optJSONArray("messages");
        JSONArray result = new JSONArray();
        if (list != null) {
            Set<String> deleted = deleted(account);
            // an account added later sees only the messages sent after it signed in
            int since = UserConfig.getInstance(account).loginTime;
            for (int i = 0; i < list.length(); i++) {
                JSONObject m = list.optJSONObject(i);
                if (m != null && m.optInt("date") >= since && !deleted.contains(String.valueOf(m.optInt("id")))) {
                    result.put(m);
                }
            }
        }
        return result;
    }

    /** Deletes these messages for this account on this phone (they stay on the server). */
    public static void delete(int account, Collection<Integer> ids) {
        Set<String> deleted = new HashSet<>(deleted(account));
        for (int id : ids) {
            deleted.add(String.valueOf(id));
        }
        HumoyServer.prefs().edit().putStringSet("chat_deleted_" + user(account), deleted).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    /** Deletes the whole chat for this account; it comes back with the next new message. */
    public static void deleteAll(int account) {
        JSONArray all = messages(account);
        HashSet<Integer> ids = new HashSet<>();
        for (int i = 0; i < all.length(); i++) {
            ids.add(all.optJSONObject(i).optInt("id"));
        }
        delete(account, ids);
    }

    public static JSONObject last(int account) {
        JSONArray list = messages(account);
        return list.length() == 0 ? null : list.optJSONObject(list.length() - 1);
    }

    public static int lastDate(int account) {
        JSONObject last = last(account);
        return last == null ? 0 : last.optInt("date");
    }

    public static int unreadCount(int account) {
        int read = HumoyServer.prefs().getInt("chat_read_" + user(account), 0);
        JSONArray list = messages(account);
        int count = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject m = list.optJSONObject(i);
            if (m != null && m.optInt("id") > read) {
                count++;
            }
        }
        return count;
    }

    public static void markRead(int account) {
        JSONObject last = last(account);
        if (last == null) {
            return;
        }
        String key = "chat_read_" + user(account);
        if (last.optInt("id") > HumoyServer.prefs().getInt(key, 0)) {
            HumoyServer.prefs().edit().putInt(key, last.optInt("id")).apply();
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    /** The line under the chat's name: the last message. */
    public static String preview(int account) {
        JSONObject last = last(account);
        if (last == null) {
            return "";
        }
        String title = last.optString("title");
        String text = last.optString("text").replace('\n', ' ');
        return title.isEmpty() ? text : text.isEmpty() ? title : title + ": " + text;
    }
}
