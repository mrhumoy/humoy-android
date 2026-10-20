/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Humoy's own server: the app checks in (version, phone model, language; a random device id, nothing
 * from Telegram) and gets back the ads, the announcement, the latest version and the server
 * addresses to use. The server can move the app to a new domain: the addresses it sends are kept
 * and tried in order, with the built-in ones as a fallback.
 */
public class HumoyServer {

    private static final String[] BUILT_IN = {"https://example.com/api/"}; // your Humoy server
    private static final String SECRET = ""; // your server's app_secret
    private static final long INTERVAL = 2 * 60 * 1000L;

    private static long lastSync;
    private static boolean syncing;

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoyserver", Context.MODE_PRIVATE);
    }

    private static String deviceId() {
        String id = prefs().getString("device", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs().edit().putString("device", id).apply();
        }
        return id;
    }

    public static String appVersion() {
        try {
            Context c = ApplicationLoader.applicationContext;
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static ArrayList<String> bases() {
        ArrayList<String> list = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs().getString("bases", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                String b = saved.optString(i);
                if (b.startsWith("https://") && !list.contains(b)) {
                    list.add(b);
                }
            }
        } catch (Exception ignore) {
        }
        for (String b : BUILT_IN) {
            if (!list.contains(b)) {
                list.add(b);
            }
        }
        return list;
    }

    /** A page on Humoy's site, on the domain the app uses now (it follows a domain change). */
    public static String siteUrl(String path) {
        String base = bases().get(0);
        if (base.endsWith("api/")) {
            base = base.substring(0, base.length() - 4);
        }
        return base + path;
    }

    /** The last answer of the server, or an empty object. */
    public static JSONObject state() {
        try {
            return new JSONObject(prefs().getString("state", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---- ad events, sent with the next check-in

    public static synchronized void countEvent(String type, int adId) {
        try {
            JSONObject events = new JSONObject(prefs().getString("events", "{}"));
            String key = type + ":" + adId;
            events.put(key, events.optInt(key) + 1);
            prefs().edit().putString("events", events.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    private static synchronized void removeSentEvents(JSONObject sent) {
        try {
            JSONObject events = new JSONObject(prefs().getString("events", "{}"));
            for (Iterator<String> it = sent.keys(); it.hasNext(); ) {
                String key = it.next();
                int left = events.optInt(key) - sent.optInt(key);
                if (left > 0) {
                    events.put(key, left);
                } else {
                    events.remove(key);
                }
            }
            prefs().edit().putString("events", events.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    // ---- check-in

    public static void sync(boolean force) {
        if (syncing || !force && SystemClock.elapsedRealtime() - lastSync < INTERVAL && lastSync != 0) {
            return;
        }
        syncing = true;
        lastSync = SystemClock.elapsedRealtime();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject events = new JSONObject(prefs().getString("events", "{}"));
                JSONObject body = new JSONObject();
                body.put("device", deviceId());
                body.put("version", appVersion());
                body.put("android", Build.VERSION.RELEASE);
                body.put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim());
                body.put("lang", LocaleController.getInstance().getCurrentLocale().getLanguage());
                body.put("accounts", UserConfig.getActivatedAccountsCount());
                body.put("events", events);
                String request = body.toString();
                for (String base : bases()) {
                    String answer = post(base + "hello.php", request);
                    if (answer == null) {
                        continue;
                    }
                    JSONObject json = new JSONObject(answer);
                    if (!json.optBoolean("ok")) {
                        continue;
                    }
                    removeSentEvents(events);
                    SharedPreferences.Editor editor = prefs().edit().putString("state", answer);
                    JSONArray api = json.optJSONArray("api");
                    if (api != null && api.length() > 0) {
                        editor.putString("bases", api.toString());
                    }
                    editor.apply();
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.humoyServerUpdated);
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                    });
                    break;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                syncing = false;
            }
        });
    }

    private static String post(String url, String body) {
        HttpURLConnection connection = null;
        try {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Humoy-Sign", sign(data));
            try (OutputStream out = connection.getOutputStream()) {
                out.write(data);
            }
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    result.write(buffer, 0, n);
                    if (result.size() > 512 * 1024) {
                        return null;
                    }
                }
                return result.toString("UTF-8");
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String sign(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Utilities.bytesToHex(mac.doFinal(data)).toLowerCase();
    }

    /** Whether a link points at a picture file (a page or a post link isn't one). */
    public static boolean isImageUrl(String url) {
        return url != null && url.startsWith("https://") && url.toLowerCase().matches("^[^?#]+\\.(jpe?g|png|webp|gif)([?#].*)?$");
    }

    /** -1, 0 or 1 comparing dotted versions such as 1.0.2 and 1.0.10. */
    public static int compareVersions(String a, String b) {
        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b)) {
            return 0;
        }
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Utilities.parseInt(x[i]) : 0;
            int q = i < y.length ? Utilities.parseInt(y[i]) : 0;
            if (p != q) {
                return p < q ? -1 : 1;
            }
        }
        return 0;
    }
}
