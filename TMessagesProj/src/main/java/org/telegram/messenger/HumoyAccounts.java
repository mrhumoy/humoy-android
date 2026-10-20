/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Humoy's account list settings: the order accounts are shown in, accounts hidden from the account
 * switchers, and names given to accounts on this phone. Kept by user id, so they follow an account
 * whichever slot it is in.
 */
public class HumoyAccounts {

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoyaccounts", Context.MODE_PRIVATE);
    }

    private static long userIdOf(int account) {
        return UserConfig.getInstance(account).getClientUserId();
    }

    // ---- order

    private static ArrayList<Long> order() {
        ArrayList<Long> ids = new ArrayList<>();
        String s = prefs().getString("order", "");
        for (String part : s.split(",")) {
            try {
                if (!part.isEmpty()) {
                    ids.add(Long.parseLong(part));
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return ids;
    }

    private static void saveOrder(List<Long> ids) {
        prefs().edit().putString("order", TextUtils.join(",", ids)).apply();
    }

    /** Accounts in the user's order; the ones never ordered follow by login time, as Telegram sorts them. */
    public static final Comparator<Integer> COMPARATOR = (a, b) -> {
        ArrayList<Long> order = order();
        int ia = order.indexOf(userIdOf(a));
        int ib = order.indexOf(userIdOf(b));
        if (ia != ib) {
            if (ia == -1) {
                return 1;
            }
            if (ib == -1) {
                return -1;
            }
            return Integer.compare(ia, ib);
        }
        return Long.compare(UserConfig.getInstance(a).loginTime, UserConfig.getInstance(b).loginTime);
    };

    /** Saves the order of these accounts as shown (account slot numbers, top first). */
    public static void setOrder(List<Integer> accounts) {
        ArrayList<Long> ids = new ArrayList<>();
        for (int account : accounts) {
            long id = userIdOf(account);
            if (id != 0) {
                ids.add(id);
            }
        }
        saveOrder(ids);
    }

    /** All signed-in accounts in the user's order. */
    public static ArrayList<Integer> allAccounts() {
        ArrayList<Integer> list = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                list.add(a);
            }
        }
        Collections.sort(list, COMPARATOR);
        return list;
    }

    /** Puts this account first. */
    public static void moveToTop(int account) {
        ArrayList<Integer> list = allAccounts();
        list.remove((Integer) account);
        list.add(0, account);
        setOrder(list);
    }

    // ---- hidden

    private static Set<String> hidden() {
        return new HashSet<>(prefs().getStringSet("hidden", Collections.emptySet()));
    }

    /** Hidden accounts aren't offered in the account switchers; the one in use is always shown. */
    public static boolean isHidden(int account) {
        long id = userIdOf(account);
        return id != 0 && hidden().contains(String.valueOf(id));
    }

    public static void setHidden(int account, boolean hide) {
        long id = userIdOf(account);
        if (id == 0) {
            return;
        }
        Set<String> set = hidden();
        if (hide) {
            set.add(String.valueOf(id));
        } else {
            set.remove(String.valueOf(id));
        }
        prefs().edit().putStringSet("hidden", set).apply();
    }

    /** Whether a switcher should list this account (the current one is listed by the caller as it likes). */
    public static boolean showInSwitcher(int account, int currentAccount) {
        return account == currentAccount || !isHidden(account);
    }

    /** Accounts the manage screen lists: all but the hidden ones (the one in use always shows). */
    public static ArrayList<Integer> visibleAccounts(int currentAccount) {
        ArrayList<Integer> list = allAccounts();
        filterForSwitcher(list, currentAccount);
        return list;
    }

    /** Hidden accounts, in the user's order. */
    public static ArrayList<Integer> hiddenAccounts() {
        ArrayList<Integer> list = allAccounts();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!isHidden(list.get(i))) {
                list.remove(i);
            }
        }
        return list;
    }

    // ---- PIN that opens the hidden accounts

    public static boolean hasPin() {
        return HumoyPin.hasPin(HumoyPin.SCOPE_ACCOUNTS);
    }

    public static void setPin(String pin) {
        HumoyPin.setPin(HumoyPin.SCOPE_ACCOUNTS, pin);
    }

    public static boolean checkPin(String pin) {
        return HumoyPin.checkPin(HumoyPin.SCOPE_ACCOUNTS, pin);
    }

    /** Removes from a switcher list the hidden accounts, except the one in use. */
    public static void filterForSwitcher(List<Integer> accounts, int currentAccount) {
        for (int i = accounts.size() - 1; i >= 0; i--) {
            if (!showInSwitcher(accounts.get(i), currentAccount)) {
                accounts.remove(i);
            }
        }
    }

    // ---- names

    /** The name given on this phone, or null. */
    public static String getCustomName(int account) {
        long id = userIdOf(account);
        String name = id == 0 ? null : prefs().getString("name" + id, null);
        return TextUtils.isEmpty(name) ? null : name;
    }

    public static void setCustomName(int account, String name) {
        long id = userIdOf(account);
        if (id == 0) {
            return;
        }
        if (TextUtils.isEmpty(name == null ? null : name.trim())) {
            prefs().edit().remove("name" + id).apply();
        } else {
            prefs().edit().putString("name" + id, name.trim()).apply();
        }
    }

    /** What account lists show for this account: the name given here, else the account's own name. */
    public static String name(int account) {
        String custom = getCustomName(account);
        if (custom != null) {
            return custom;
        }
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        return UserObject.getUserName(user);
    }
}
