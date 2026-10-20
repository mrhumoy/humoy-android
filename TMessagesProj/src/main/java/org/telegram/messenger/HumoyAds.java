/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Humoy's own ads, from Humoy's server, in two looks: "top" (under the pinned chats) and "inline"
 * (among the chats). Kept per account: each account sees an ad on one app opening, then the ad rests
 * (a day split by "per_day") before it comes back; opened or closed, it leaves the list. Views, clicks
 * and closes are counted for the ad's statistics.
 */
public class HumoyAds {

    public static final String STYLE_TOP = "top";
    public static final String STYLE_INLINE = "inline";

    private static final HashMap<String, JSONObject> sessionAds = new HashMap<>();
    private static final HashSet<String> viewedThisSession = new HashSet<>();
    private static final HashSet<String> hideOnReturn = new HashSet<>();

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static String user(int account) {
        return String.valueOf(UserConfig.getInstance(account).getClientUserId());
    }

    private static Set<String> closed(int account) {
        return HumoyServer.prefs().getStringSet("ads_closed_" + user(account), Collections.emptySet());
    }

    private static int shownToday(int account, int id) {
        return HumoyServer.prefs().getInt("ads_day_" + today() + "_" + user(account) + "_" + id, 0);
    }

    private static String styleOf(JSONObject ad) {
        return STYLE_INLINE.equals(ad.optString("style")) ? STYLE_INLINE : STYLE_TOP;
    }

    /** The ad of this style for this account now, or null. Stays the same while the app is open. */
    public static JSONObject current(int account, String style) {
        JSONArray ads = HumoyServer.state().optJSONArray("ads");
        String key = account + ":" + style;
        if (ads == null) {
            sessionAds.remove(key);
            return null;
        }
        Set<String> closed = closed(account);
        JSONObject sessionAd = sessionAds.get(key);
        if (sessionAd != null) {
            int id = sessionAd.optInt("id");
            for (int i = 0; i < ads.length(); i++) {
                JSONObject ad = ads.optJSONObject(i);
                if (ad != null && ad.optInt("id") == id && styleOf(ad).equals(style) && !closed.contains(String.valueOf(id))) {
                    sessionAds.put(key, ad);
                    return ad;
                }
            }
            sessionAds.remove(key);
        }
        for (int i = 0; i < ads.length(); i++) {
            JSONObject ad = ads.optJSONObject(i);
            if (ad == null || !styleOf(ad).equals(style)) {
                continue;
            }
            int id = ad.optInt("id");
            int perDay = Math.max(1, ad.optInt("per_day", 1));
            if (closed.contains(String.valueOf(id)) || shownToday(account, id) >= perDay) {
                continue;
            }
            if (resting.contains(key + ":" + id)) {
                continue;
            }
            long rest = Math.max(60 * 60 * 1000L, 24 * 60 * 60 * 1000L / perDay);
            if (System.currentTimeMillis() - HumoyServer.prefs().getLong("ads_last_" + user(account) + "_" + id, 0) < rest) {
                continue;
            }
            sessionAds.put(key, ad);
            return ad;
        }
        return null;
    }

    /** The ad came on screen: counted once per app opening and account. */
    public static void onShown(int account, int id) {
        if (!viewedThisSession.add(account + ":" + id)) {
            return;
        }
        String u = user(account);
        String key = "ads_day_" + today() + "_" + u + "_" + id;
        HumoyServer.prefs().edit().putInt(key, HumoyServer.prefs().getInt(key, 0) + 1).putLong("ads_last_" + u + "_" + id, System.currentTimeMillis()).apply();
        HumoyServer.countEvent("v", id);
    }

    /** Opened: this ad leaves the list once the user is back from it; the other one stays. */
    public static void onClick(int account, int id, String style) {
        HumoyServer.countEvent("c", id);
        hideOnReturn.add(account + ":" + style);
    }

    /** The chat list came back on screen: an ad the user opened is gone now. */
    public static void onListResumed(int account) {
        boolean changed = false;
        for (String style : new String[]{STYLE_TOP, STYLE_INLINE}) {
            String key = account + ":" + style;
            if (hideOnReturn.remove(key)) {
                JSONObject ad = sessionAds.remove(key);
                if (ad != null) {
                    resting.add(key + ":" + ad.optInt("id"));
                }
                changed = true;
            }
        }
        if (changed) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    public static void onClose(int id, int account, String style) {
        Set<String> set = new HashSet<>(closed(account));
        set.add(String.valueOf(id));
        HumoyServer.prefs().edit().putStringSet("ads_closed_" + user(account), set).apply();
        HumoyServer.countEvent("x", id);
        sessionAds.remove(account + ":" + style);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    // ads opened during this app opening: not shown again until the next one
    private static final HashSet<String> resting = new HashSet<>();

    /** The app went to the background: the next opening may show other ads (or none for now). */
    public static void onAppHidden() {
        if (sessionAds.isEmpty()) {
            return;
        }
        sessionAds.clear();
        viewedThisSession.clear();
        hideOnReturn.clear();
        resting.clear();
        // rebuild the lists now, so the ads are gone when the app comes back
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
    }
}
