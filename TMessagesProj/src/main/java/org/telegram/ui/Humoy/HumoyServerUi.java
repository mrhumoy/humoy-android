/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.HumoyServer;
import org.telegram.ui.ActionBar.BaseFragment;

/** Shows what Humoy's server asks for: a newer version to install, or an announcement (once). */
public class HumoyServerUi {

    private static final long LATER = 24 * 60 * 60 * 1000L;

    private static boolean retryPosted;

    public static void check(BaseFragment fragment) {
        // shown over the top screen (the chat list lives inside the main tabs screen)
        BaseFragment last = org.telegram.ui.LaunchActivity.getLastFragment();
        if (last != null) {
            fragment = last;
        }
        if (fragment == null || fragment.getParentActivity() == null || fragment.getVisibleDialog() != null) {
            return;
        }
        JSONObject state = HumoyServer.state();
        int result = showUpdate(fragment, state.optJSONObject("update"));
        if (result == 0) {
            result = showAnnouncement(fragment, state.optJSONObject("announcement"));
        }
        if (result < 0 && !retryPosted) {
            // the screen was busy (opening, a transition): try once more a little later
            retryPosted = true;
            BaseFragment f = fragment;
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                retryPosted = false;
                check(f);
            }, 1500);
        }
    }

    /** 1 shown, 0 nothing to show, -1 couldn't show now. */
    private static int showUpdate(BaseFragment fragment, JSONObject update) {
        if (update == null) {
            return 0;
        }
        String latest = update.optString("version");
        String url = update.optString("url").trim();
        if (!url.isEmpty() && !url.contains("://") && !url.startsWith("market:")) {
            url = "https://" + url;
        }
        String current = HumoyServer.appVersion();
        if (TextUtils.isEmpty(latest) || !(url.startsWith("https://") || url.startsWith("market://")) || HumoyServer.compareVersions(current, latest) >= 0) {
            return 0;
        }
        String min = update.optString("min_version");
        boolean forced = !TextUtils.isEmpty(min) && HumoyServer.compareVersions(current, min) < 0;
        String laterKey = "update_later_" + latest;
        if (!forced && System.currentTimeMillis() - HumoyServer.prefs().getLong(laterKey, 0) < LATER) {
            return 0;
        }
        return HumoySheets.showUpdate(fragment, latest, update.optString("changelog"), url, forced,
                () -> HumoyServer.prefs().edit().putLong(laterKey, System.currentTimeMillis()).apply()) != null ? 1 : -1;
    }

    private static int showAnnouncement(BaseFragment fragment, JSONObject announcement) {
        if (announcement == null) {
            return 0;
        }
        int id = announcement.optInt("id");
        String seenKey = "announce_seen_" + id;
        if (id == 0 || HumoyServer.prefs().getBoolean(seenKey, false)) {
            return 0;
        }
        if (!HumoySheets.showAnnouncement(fragment, announcement)) {
            return -1;
        }
        HumoyServer.prefs().edit().putBoolean(seenKey, true).apply();
        return 1;
    }
}
