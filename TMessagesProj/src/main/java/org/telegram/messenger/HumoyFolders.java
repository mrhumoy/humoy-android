/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Humoy's folder tabs for one account: built-in "Personal / Groups / Channels / Bots" folders that live
 * only on this device (never sent to Telegram, so they don't count against the account's folder limit),
 * merged with the account's own Telegram folders. The order and which tabs are hidden are kept per account.
 */
public class HumoyFolders {

    public static final String KEY_ALL = "all";
    public static final String KEY_PERSONAL = "p";
    public static final String KEY_GROUPS = "g";
    public static final String KEY_CHANNELS = "c";
    public static final String KEY_BOTS = "b";
    public static final String[] BUILT_IN = {KEY_ALL, KEY_PERSONAL, KEY_GROUPS, KEY_CHANNELS, KEY_BOTS};

    // Built-in folders use ids no server folder can have.
    private static final int ID_PERSONAL = -101;
    private static final int ID_GROUPS = -102;
    private static final int ID_CHANNELS = -103;
    private static final int ID_BOTS = -104;

    private static final HumoyFolders[] instances = new HumoyFolders[UserConfig.MAX_ACCOUNT_COUNT];

    public static HumoyFolders getInstance(int account) {
        HumoyFolders local = instances[account];
        if (local == null) {
            synchronized (HumoyFolders.class) {
                local = instances[account];
                if (local == null) {
                    instances[account] = local = new HumoyFolders(account);
                }
            }
        }
        return local;
    }

    private final int currentAccount;
    private final MessagesController.DialogFilter allFilter;
    private final MessagesController.DialogFilter personal;
    private final MessagesController.DialogFilter groups;
    private final MessagesController.DialogFilter channels;
    private final MessagesController.DialogFilter bots;

    private final ArrayList<MessagesController.DialogFilter> tabFilters = new ArrayList<>();
    // The tab list is rebuilt only when the account's Telegram folders or Humoy's order/hidden/empty sets change.
    private int localVersion;
    private int builtForVersion = -1;
    private int builtForServerHash;
    private ArrayList<MessagesController.DialogFilter> builtForServerList;

    // Unread counts of the built-in folders, counted together in one pass and reused until the chats change.
    private final int[] unreadCounts = new int[4];
    private boolean unreadCountsValid;
    private final ArrayList<String> order = new ArrayList<>();
    private final HashSet<String> hidden = new HashSet<>();
    // Built-in folders with no chats yet are left out until they get one.
    private final HashSet<String> empty = new HashSet<>();

    private HumoyFolders(int account) {
        currentAccount = account;
        allFilter = new MessagesController.DialogFilter();
        allFilter.id = 0;
        personal = builtIn(ID_PERSONAL, MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS);
        groups = builtIn(ID_GROUPS, MessagesController.DIALOG_FILTER_FLAG_GROUPS);
        channels = builtIn(ID_CHANNELS, MessagesController.DIALOG_FILTER_FLAG_CHANNELS);
        bots = builtIn(ID_BOTS, MessagesController.DIALOG_FILTER_FLAG_BOTS);
        load();
    }

    private static MessagesController.DialogFilter builtIn(int id, int flags) {
        MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
        filter.id = id;
        filter.flags = flags | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
        return filter;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("humoyfolders" + currentAccount, Context.MODE_PRIVATE);
    }

    private void load() {
        SharedPreferences p = prefs();
        order.clear();
        String savedOrder = p.getString("order", "");
        if (!TextUtils.isEmpty(savedOrder)) {
            for (String key : savedOrder.split(",")) {
                if (!TextUtils.isEmpty(key)) {
                    order.add(key);
                }
            }
        }
        hidden.clear();
        String savedHidden = p.getString("hidden", "");
        if (!TextUtils.isEmpty(savedHidden)) {
            for (String key : savedHidden.split(",")) {
                if (!TextUtils.isEmpty(key)) {
                    hidden.add(key);
                }
            }
        }
    }

    private void save() {
        prefs().edit()
                .putString("order", TextUtils.join(",", order))
                .putString("hidden", TextUtils.join(",", hidden))
                .apply();
    }

    public static boolean isBuiltIn(MessagesController.DialogFilter filter) {
        return filter != null && filter.id <= ID_PERSONAL && filter.id >= ID_BOTS;
    }

    public static String keyOf(MessagesController.DialogFilter filter) {
        if (filter.isDefault()) {
            return KEY_ALL;
        }
        switch (filter.id) {
            case ID_PERSONAL: return KEY_PERSONAL;
            case ID_GROUPS: return KEY_GROUPS;
            case ID_CHANNELS: return KEY_CHANNELS;
            case ID_BOTS: return KEY_BOTS;
        }
        return "s" + filter.id;
    }

    public static String titleOf(String key) {
        switch (key) {
            case KEY_ALL: return HumoyStrings.get("FolderAll");
            case KEY_PERSONAL: return HumoyStrings.get("FolderPersonal");
            case KEY_GROUPS: return HumoyStrings.get("FolderGroups");
            case KEY_CHANNELS: return HumoyStrings.get("FolderChannels");
            case KEY_BOTS: return HumoyStrings.get("FolderBots");
        }
        return null;
    }

    public static int iconOf(MessagesController.DialogFilter filter) {
        switch (keyOf(filter)) {
            case KEY_ALL: return R.drawable.msg_discussion;
            case KEY_PERSONAL: return R.drawable.msg_contacts;
            case KEY_GROUPS: return R.drawable.msg_groups;
            case KEY_CHANNELS: return R.drawable.msg_channel;
            case KEY_BOTS: return R.drawable.msg_bots;
        }
        return R.drawable.msg_folders;
    }

    /** Folder name shown above the chat list when the tab is selected. */
    public String nameOf(MessagesController.DialogFilter filter) {
        String builtInTitle = titleOf(keyOf(filter));
        return builtInTitle != null ? builtInTitle : filter.name;
    }

    /**
     * Icon-only tab label; the span takes the tab's text color, so selected/unselected tinting keeps working.
     * The hidden text under the icon is the folder's key: FilterTabsView decides whether a recycled tab must
     * redraw by comparing text only, so every folder needs different text or it would keep another folder's icon.
     */
    public static CharSequence tabLabel(MessagesController.DialogFilter filter) {
        String key = keyOf(filter);
        SpannableString label = new SpannableString(key);
        ColoredImageSpan span = new ColoredImageSpan(iconOf(filter));
        span.setSize(AndroidUtilities.dp(22));
        label.setSpan(span, 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
    }

    private static CharSequence allTabLabel;

    public static CharSequence allTabLabel() {
        if (allTabLabel == null) {
            MessagesController.DialogFilter all = new MessagesController.DialogFilter();
            all.id = 0;
            allTabLabel = tabLabel(all);
        }
        return allTabLabel;
    }

    private MessagesController.DialogFilter builtInByKey(String key) {
        switch (key) {
            case KEY_PERSONAL: return personal;
            case KEY_GROUPS: return groups;
            case KEY_CHANNELS: return channels;
            case KEY_BOTS: return bots;
        }
        return null;
    }

    /** Tabs in display order. FilterTabsView reorders this list in place while the user drags tabs. */
    public ArrayList<MessagesController.DialogFilter> getTabFilters() {
        ArrayList<MessagesController.DialogFilter> server = MessagesController.getInstance(currentAccount).getDialogFilters();
        int serverHash = server.size();
        for (int i = 0; i < server.size(); i++) {
            MessagesController.DialogFilter f = server.get(i);
            serverHash = serverHash * 31 + f.id * 2 + (f.locked ? 1 : 0);
        }
        if (builtForVersion != localVersion || builtForServerHash != serverHash || builtForServerList != server) {
            builtForVersion = localVersion;
            builtForServerHash = serverHash;
            builtForServerList = server;
            rebuild(server);
        }
        return tabFilters;
    }

    private void rebuild(ArrayList<MessagesController.DialogFilter> server) {
        MessagesController.DialogFilter serverDefault = null;
        ArrayList<MessagesController.DialogFilter> serverFolders = new ArrayList<>();
        for (int i = 0; i < server.size(); i++) {
            MessagesController.DialogFilter f = server.get(i);
            if (f.isDefault()) {
                serverDefault = f;
            } else {
                serverFolders.add(f);
            }
        }
        // Keys in display order: the saved order first, then anything new (built-ins first time, new folders at the end).
        ArrayList<String> keys = new ArrayList<>();
        for (String key : order) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (String key : BUILT_IN) {
            if (!keys.contains(key)) {
                keys.add(Math.min(indexOfBuiltIn(key), keys.size()), key);
            }
        }
        for (int i = 0; i < serverFolders.size(); i++) {
            String key = "s" + serverFolders.get(i).id;
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        tabFilters.clear();
        for (String key : keys) {
            if (hidden.contains(key)) {
                continue;
            }
            MessagesController.DialogFilter filter;
            if (KEY_ALL.equals(key)) {
                filter = serverDefault != null ? serverDefault : allFilter;
            } else if (key.startsWith("s")) {
                filter = null;
                for (int i = 0; i < serverFolders.size(); i++) {
                    if (key.equals("s" + serverFolders.get(i).id)) {
                        filter = serverFolders.get(i);
                        break;
                    }
                }
            } else {
                filter = empty.contains(key) ? null : builtInByKey(key);
            }
            if (filter != null) {
                tabFilters.add(filter);
            }
        }
    }

    private static int indexOfBuiltIn(String key) {
        for (int i = 0; i < BUILT_IN.length; i++) {
            if (BUILT_IN[i].equals(key)) {
                return i;
            }
        }
        return BUILT_IN.length;
    }

    /** Re-checks which built-in folders have chats; returns true if the visible tabs change. */
    public boolean checkEmptyFolders() {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (!controller.dialogsLoaded) {
            return false;
        }
        ArrayList<TLRPC.Dialog> dialogs = controller.getDialogs(0);
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        HashSet<String> nowEmpty = new HashSet<>();
        for (String key : new String[]{KEY_PERSONAL, KEY_GROUPS, KEY_CHANNELS, KEY_BOTS}) {
            MessagesController.DialogFilter filter = builtInByKey(key);
            boolean found = false;
            for (int i = 0; i < dialogs.size(); i++) {
                TLRPC.Dialog d = dialogs.get(i);
                if (filter.includesDialog(accountInstance, d.id, d)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                nowEmpty.add(key);
            }
        }
        if (nowEmpty.equals(empty)) {
            return false;
        }
        empty.clear();
        empty.addAll(nowEmpty);
        localVersion++;
        return true;
    }

    /** The chat list changed; unread counts are recounted the next time a tab asks for one. */
    public void invalidateUnreadCounts() {
        unreadCountsValid = false;
    }

    /** Unread chats in a built-in folder, counted like Telegram counts its folders (muted chats left out). */
    public int unreadCount(MessagesController.DialogFilter filter) {
        if (!unreadCountsValid) {
            countUnread();
        }
        switch (filter.id) {
            case ID_PERSONAL: return unreadCounts[0];
            case ID_GROUPS: return unreadCounts[1];
            case ID_CHANNELS: return unreadCounts[2];
            case ID_BOTS: return unreadCounts[3];
        }
        return 0;
    }

    private void countUnread() {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.Dialog> dialogs = controller.getDialogs(0);
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        MessagesController.DialogFilter[] filters = {personal, groups, channels, bots};
        unreadCounts[0] = unreadCounts[1] = unreadCounts[2] = unreadCounts[3] = 0;
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog d = dialogs.get(i);
            if (d.unread_count <= 0 && !d.unread_mark || controller.isDialogMuted(d.id, 0)) {
                continue;
            }
            // The four folders don't overlap, so each chat lands in at most one of them.
            for (int f = 0; f < filters.length; f++) {
                if (filters[f].includesDialog(accountInstance, d.id, d)) {
                    unreadCounts[f]++;
                    break;
                }
            }
        }
        unreadCountsValid = true;
    }

    public boolean isHidden(String key) {
        return hidden.contains(key);
    }

    public void setHidden(String key, boolean hide) {
        if (hide ? hidden.add(key) : hidden.remove(key)) {
            localVersion++;
            save();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogFiltersUpdated);
        }
    }

    /** The user finished dragging tabs: remember Humoy's order and send the order of Telegram folders to the server. */
    public void commitTabOrder() {
        ArrayList<String> newOrder = new ArrayList<>();
        for (int i = 0; i < tabFilters.size(); i++) {
            newOrder.add(keyOf(tabFilters.get(i)));
        }
        // Hidden or empty tabs keep their earlier relative position.
        for (String key : order) {
            if (!newOrder.contains(key)) {
                newOrder.add(Math.min(order.indexOf(key), newOrder.size()), key);
            }
        }
        order.clear();
        order.addAll(newOrder);
        localVersion++;
        save();

        MessagesController controller = MessagesController.getInstance(currentAccount);
        ArrayList<MessagesController.DialogFilter> server = controller.dialogFilters;
        ArrayList<MessagesController.DialogFilter> sorted = new ArrayList<>();
        for (String key : order) {
            for (int i = 0; i < server.size(); i++) {
                MessagesController.DialogFilter f = server.get(i);
                if (!sorted.contains(f) && key.equals(keyOf(f))) {
                    sorted.add(f);
                }
            }
        }
        for (int i = 0; i < server.size(); i++) {
            if (!sorted.contains(server.get(i))) {
                sorted.add(server.get(i));
            }
        }
        boolean changed = false;
        for (int i = 0; i < sorted.size(); i++) {
            if (server.get(i) != sorted.get(i)) {
                changed = true;
            }
            sorted.get(i).order = i;
        }
        if (!changed) {
            return;
        }
        server.clear();
        server.addAll(sorted);
        MessagesStorage.getInstance(currentAccount).saveDialogFiltersOrder();
        TLRPC.TL_messages_updateDialogFiltersOrder req = new TLRPC.TL_messages_updateDialogFiltersOrder();
        for (int i = 0; i < sorted.size(); i++) {
            req.order.add(sorted.get(i).isDefault() ? 0 : sorted.get(i).id);
        }
        controller.lockFiltersInternal();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
        });
    }
}
