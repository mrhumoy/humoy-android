/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The 4-digit PINs of Humoy's private places, one per place: hidden accounts, locked chats and the
 * hidden section. Kept as a salted SHA-256; each can also be opened with a fingerprint.
 */
public class HumoyPin {

    public static final int SCOPE_ACCOUNTS = 0;
    public static final int SCOPE_LOCKED = 1;
    public static final int SCOPE_HIDDEN = 2;

    private static SharedPreferences prefs(int scope) {
        // the hidden accounts PIN stays where it was first kept
        String name = scope == SCOPE_ACCOUNTS ? "humoyaccounts" : "humoypin";
        return ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static String key(int scope, String name) {
        if (scope == SCOPE_ACCOUNTS) {
            return name;
        }
        return (scope == SCOPE_LOCKED ? "locked_" : "hidden_") + name;
    }

    public static boolean hasPin(int scope) {
        return prefs(scope).getString(key(scope, "pinHash"), null) != null;
    }

    public static void setPin(int scope, String pin) {
        byte[] salt = new byte[16];
        Utilities.random.nextBytes(salt);
        String saltHex = Utilities.bytesToHex(salt);
        prefs(scope).edit().putString(key(scope, "pinSalt"), saltHex).putString(key(scope, "pinHash"), hash(saltHex, pin)).apply();
    }

    public static void clearPin(int scope) {
        prefs(scope).edit().remove(key(scope, "pinSalt")).remove(key(scope, "pinHash")).apply();
    }

    public static boolean checkPin(int scope, String pin) {
        String saltHex = prefs(scope).getString(key(scope, "pinSalt"), null);
        String stored = prefs(scope).getString(key(scope, "pinHash"), null);
        return saltHex != null && stored != null && stored.equals(hash(saltHex, pin));
    }

    public static boolean useFingerprint(int scope) {
        return prefs(scope).getBoolean(key(scope, "finger"), true);
    }

    public static void setUseFingerprint(int scope, boolean use) {
        prefs(scope).edit().putBoolean(key(scope, "finger"), use).apply();
    }

    private static String hash(String saltHex, String pin) {
        return Utilities.bytesToHex(Utilities.computeSHA256((saltHex + ":" + pin).getBytes()));
    }
}
