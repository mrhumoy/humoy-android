/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

/** Contacts online right now, and below them the ones who were online during the last hour; updated live. */
public class HumoyOnlineContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int RECENT_SECONDS = 60 * 60;

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_USER = 1;
    private static final int VIEW_SHADOW = 2;

    // what the list shows: a header text, a user, or null for a section gap
    private final ArrayList<Object> rows = new ArrayList<>();
    private int onlineCount;
    private RecyclerListView listView;
    private Adapter adapter;
    private LinearLayout emptyView;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // statuses expire on their own: re-sort every half minute
            update();
            AndroidUtilities.runOnUIThread(this, 30_000);
        }
    };

    /** How many contacts are online right now (for the Settings row). */
    public static int countOnline(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        int now = org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime();
        int count = 0;
        ArrayList<TLRPC.TL_contact> contacts = org.telegram.messenger.ContactsController.getInstance(account).contacts;
        for (int a = 0; a < contacts.size(); a++) {
            TLRPC.User user = controller.getUser(contacts.get(a).user_id);
            if (isOnline(user, now)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOnline(TLRPC.User user, int now) {
        return user != null && !user.self && !user.bot && user.status != null && user.status.expires > now;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        AndroidUtilities.cancelRunOnUIThread(tick);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                update();
            }
        } else if (id == NotificationCenter.contactsDidLoad) {
            update();
        }
    }

    private void update() {
        int now = getConnectionsManager().getCurrentTime();
        ArrayList<TLRPC.User> online = new ArrayList<>();
        ArrayList<TLRPC.User> recent = new ArrayList<>();
        ArrayList<TLRPC.TL_contact> contacts = getContactsController().contacts;
        for (int a = 0; a < contacts.size(); a++) {
            TLRPC.User user = getMessagesController().getUser(contacts.get(a).user_id);
            if (user == null || user.self || user.bot || user.status == null) {
                continue;
            }
            if (user.status.expires > now) {
                online.add(user);
            } else if (user.status.expires > 0 && now - user.status.expires <= RECENT_SECONDS) {
                recent.add(user);
            }
        }
        java.util.Collections.sort(online, (a, b) -> UserObject.getUserName(a).compareToIgnoreCase(UserObject.getUserName(b)));
        java.util.Collections.sort(recent, (a, b) -> Integer.compare(b.status.expires, a.status.expires));

        rows.clear();
        onlineCount = online.size();
        if (!online.isEmpty()) {
            rows.add(String.format(HumoyStrings.get("OnlineNowHeader"), online.size()));
            rows.addAll(online);
            rows.add(null);
        }
        if (!recent.isEmpty()) {
            rows.add(HumoyStrings.get("OnlineRecentHeader"));
            rows.addAll(recent);
            rows.add(null);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (actionBar != null) {
            actionBar.setSubtitle(String.format(HumoyStrings.get("OnlineSubtitle"), onlineCount));
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HumoyStrings.get("OnlineContacts"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setOnItemClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof TLRPC.User) {
                Bundle args = new Bundle();
                args.putLong("user_id", ((TLRPC.User) row).id);
                presentFragment(new ChatActivity(args));
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            Object row = position >= 0 && position < rows.size() ? rows.get(position) : null;
            if (row instanceof TLRPC.User) {
                Bundle args = new Bundle();
                args.putLong("user_id", ((TLRPC.User) row).id);
                presentFragment(new ProfileActivity(args));
                return true;
            }
            return false;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        RLottieImageView duck = new RLottieImageView(context);
        duck.setAutoRepeat(true);
        duck.setAnimation(R.raw.utyan_empty, 130, 130);
        duck.playAnimation();
        emptyView.addView(duck, LayoutHelper.createLinear(130, 130));
        TextView title = new TextView(context);
        title.setText(HumoyStrings.get("OnlineEmpty"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        emptyView.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
        TextView text = new TextView(context);
        text.setText(HumoyStrings.get("OnlineEmptyInfo"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(dp(2), 1f);
        emptyView.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 40, 8, 40, 0));
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 0, 0, 0, 60));

        update();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.cancelRunOnUIThread(tick);
        tick.run();
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.cancelRunOnUIThread(tick);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_USER;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            return row instanceof String ? VIEW_HEADER : row instanceof TLRPC.User ? VIEW_USER : VIEW_SHADOW;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_HEADER) {
                view = new HeaderCell(parent.getContext());
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_USER) {
                view = new UserCell(parent.getContext(), 6, 0, false);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new ShadowSectionCell(parent.getContext());
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder.itemView instanceof HeaderCell) {
                ((HeaderCell) holder.itemView).setText((String) row);
            } else if (holder.itemView instanceof UserCell) {
                boolean divider = position + 1 < rows.size() && rows.get(position + 1) instanceof TLRPC.User;
                // a null status lets the cell write "online" in blue or "last seen 5 minutes ago"
                ((UserCell) holder.itemView).setData(row, null, null, 0, divider);
            }
        }
    }
}
