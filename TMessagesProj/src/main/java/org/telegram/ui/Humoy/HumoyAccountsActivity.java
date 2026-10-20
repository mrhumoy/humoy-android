/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HumoyAccounts;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Accounts on this phone, on a card: drag to reorder, gear for make main / rename / hide / log out,
 * "Add account" at the bottom (long-press it to open the hidden accounts with the PIN).
 * The same screen in hidden mode lists the hidden accounts.
 */
public class HumoyAccountsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_ACCOUNT = 0;
    private static final int VIEW_ADD = 1;
    private static final int VIEW_INFO = 2;
    private static final int VIEW_EMPTY = 3;

    private static final int MENU_CHANGE_PIN = 1;

    private final boolean hiddenMode;
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private RecyclerListView listView;
    private Adapter adapter;

    public HumoyAccountsActivity() {
        this(false);
    }

    public HumoyAccountsActivity(boolean hiddenMode) {
        this.hiddenMode = hiddenMode;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appDidLogout);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.updateInterfaces);
            }
        }
        reload();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appDidLogout);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.updateInterfaces);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appDidLogout || id == NotificationCenter.updateInterfaces) {
            reload();
        }
    }

    private void reload() {
        accounts.clear();
        accounts.addAll(hiddenMode ? HumoyAccounts.hiddenAccounts() : HumoyAccounts.visibleAccounts(currentAccount));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HumoyStrings.get(hiddenMode ? "HiddenAccounts" : "ManageAccounts"));
        if (hiddenMode) {
            actionBar.createMenu().addItem(0, R.drawable.ic_ab_other).addSubItem(MENU_CHANGE_PIN, R.drawable.msg_secret, HumoyStrings.get("PinChange"));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CHANGE_PIN) {
                    presentFragment(new HumoyPinActivity(HumoyPinActivity.MODE_CHECK, () ->
                            presentFragment(new HumoyPinActivity(HumoyPinActivity.MODE_CREATE, () ->
                                    BulletinFactory.of(HumoyAccountsActivity.this).createSimpleBulletin(R.raw.contact_check, HumoyStrings.get("PinChanged")).show()))));
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setPadding(0, dp(12), 0, dp(12));
        listView.setClipToPadding(false);
        listView.addItemDecoration(new CardDecoration());
        listView.setOnItemClickListener((view, position) -> {
            int type = adapter.getItemViewType(position);
            if (type == VIEW_ACCOUNT) {
                int account = accounts.get(position);
                if (account != currentAccount && LaunchActivity.instance != null) {
                    LaunchActivity.instance.switchToAccount(account, true);
                }
            } else if (type == VIEW_ADD) {
                addAccount();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (adapter.getItemViewType(position) == VIEW_ADD) {
                openHidden(this);
                return true;
            }
            return false;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // long-press and drag an account to move it
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_ACCOUNT && accounts.size() > 1 ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                if (to.getItemViewType() != VIEW_ACCOUNT) {
                    return false;
                }
                int a = from.getAdapterPosition();
                int b = to.getAdapterPosition();
                Collections.swap(accounts, a, b);
                adapter.notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setPressed(false);
                    viewHolder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                // keep the order of all accounts: the moved ones here, the others where they were
                ArrayList<Integer> all = HumoyAccounts.allAccounts();
                ArrayList<Integer> merged = new ArrayList<>(accounts);
                for (int a : all) {
                    if (!merged.contains(a)) {
                        merged.add(a);
                    }
                }
                HumoyAccounts.setOrder(merged);
                adapter.notifyDataSetChanged();
            }
        }).attachToRecyclerView(listView);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    /** Opens the hidden accounts after the PIN; the first time, the PIN is created first. */
    public static void openHidden(BaseFragment fragment) {
        int mode = HumoyAccounts.hasPin() ? HumoyPinActivity.MODE_CHECK : HumoyPinActivity.MODE_CREATE;
        fragment.presentFragment(new HumoyPinActivity(mode, null).setNext(new HumoyAccountsActivity(true)));
    }

    private void addAccount() {
        Integer free = null;
        for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                free = a;
                break;
            }
        }
        if (free != null) {
            presentFragment(new LoginActivity(free));
        }
    }

    private void hide(int account) {
        Runnable doHide = () -> {
            HumoyAccounts.setHidden(account, true);
            reload();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, HumoyStrings.get("AccountHiddenDone")).show();
        };
        if (HumoyAccounts.hasPin()) {
            doHide.run();
        } else {
            presentFragment(new HumoyPinActivity(HumoyPinActivity.MODE_CREATE, doHide));
        }
    }

    private void showOptions(int account, View anchor) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        if (hiddenMode) {
            if (account != currentAccount) {
                options.add(R.drawable.msg_openprofile, HumoyStrings.get("AccountOpen"), () -> {
                    if (LaunchActivity.instance != null) {
                        LaunchActivity.instance.switchToAccount(account, true);
                    }
                });
            }
            options.add(R.drawable.msg_viewintopic, HumoyStrings.get("AccountUnhide"), () -> {
                HumoyAccounts.setHidden(account, false);
                reload();
            });
        } else {
            options.add(R.drawable.msg_home, HumoyStrings.get("AccountMakeMain"), () -> {
                HumoyAccounts.moveToTop(account);
                reload();
                if (account != currentAccount && LaunchActivity.instance != null) {
                    LaunchActivity.instance.switchToAccount(account, true);
                }
            });
        }
        options.add(R.drawable.msg_edit, HumoyStrings.get("AccountRename"), () -> rename(account));
        if (!hiddenMode) {
            options.add(R.drawable.msg_secret, HumoyStrings.get("AccountHide"), () -> hide(account));
        }
        options.addGap();
        options.add(R.drawable.msg_leave, HumoyStrings.get("AccountLogout"), true, () -> confirmLogout(account));
        options.setGravity(Gravity.RIGHT).show();
    }

    private void rename(int account) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        editText.setHintText(UserObject.getUserName(UserConfig.getInstance(account).getCurrentUser()));
        editText.setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedBold));
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setBackground(null);
        editText.setPadding(0, dp(6), 0, dp(6));
        String custom = HumoyAccounts.getCustomName(account);
        if (custom != null) {
            editText.setText(custom);
            editText.setSelection(custom.length());
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView info = new TextView(context);
        info.setText(HumoyStrings.get("AccountRenameInfo"));
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        info.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        layout.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 24, 0, 24, 8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(HumoyStrings.get("AccountRename"));
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            HumoyAccounts.setCustomName(account, editText.getText() == null ? null : editText.getText().toString());
            reload();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void confirmLogout(int account) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(HumoyStrings.get("AccountLogout"));
        builder.setMessage(String.format(HumoyStrings.get("AccountLogoutConfirm"), HumoyAccounts.name(account)));
        builder.setPositiveButton(HumoyStrings.get("AccountLogout"), (d, w) -> MessagesController.getInstance(account).performLogout(1));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    // ---- look

    /** A rounded white card behind the account and "add" rows. */
    private class CardDecoration extends RecyclerView.ItemDecoration {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            float top = Float.MAX_VALUE;
            float bottom = Float.MIN_VALUE;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof AccountCell || child instanceof AddCell) {
                    top = Math.min(top, child.getY());
                    bottom = Math.max(bottom, child.getY() + child.getHeight());
                }
            }
            if (top == Float.MAX_VALUE) {
                return;
            }
            paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            rect.set(dp(12), top, parent.getWidth() - dp(12), bottom);
            c.drawRoundRect(rect, dp(16), dp(16), paint);
        }
    }

    private class AccountCell extends FrameLayout {

        private final BackupImageView avatar;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final View currentBadge;
        private final TextView name;
        private final TextView subtitle;
        private final TextView counter;
        private boolean divider;
        int account;

        AccountCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackground(Theme.getSelectorDrawable(false));

            avatar = new BackupImageView(context);
            avatar.setRoundRadius(dp(24));
            addView(avatar, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 26, 0, 0, 0));

            // a green dot on the avatar of the account in use
            currentBadge = new View(context);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFF34C759);
            dot.setStroke(dp(2), getThemedColor(Theme.key_windowBackgroundWhite));
            currentBadge.setBackground(dot);
            addView(currentBadge, LayoutHelper.createFrame(15, 15, Gravity.LEFT | Gravity.CENTER_VERTICAL, 62, 17, 0, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 88, 0, 128, 0));

            counter = new TextView(context);
            counter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            counter.setTypeface(AndroidUtilities.bold());
            counter.setTextColor(0xFFFFFFFF);
            counter.setGravity(Gravity.CENTER);
            counter.setMinWidth(dp(24));
            counter.setPadding(dp(8), 0, dp(8), 0);
            counter.setBackground(Theme.createRoundRectDrawable(dp(11), getThemedColor(Theme.key_featuredStickers_addButton)));
            addView(counter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 64, 0));

            ImageView settings = new ImageView(context);
            settings.setImageResource(R.drawable.msg_settings);
            settings.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon));
            settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
            settings.setPadding(dp(8), dp(8), dp(8), dp(8));
            settings.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            settings.setOnClickListener(v -> showOptions(account, v));
            addView(settings, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
        }

        void set(int account, boolean divider) {
            this.account = account;
            this.divider = divider;
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            avatarDrawable.setInfo(account, user);
            avatar.getImageReceiver().setCurrentAccount(account);
            avatar.setForUserOrChat(user, avatarDrawable);
            boolean current = account == currentAccount;
            currentBadge.setVisibility(current ? VISIBLE : GONE);
            name.setText(HumoyAccounts.name(account));
            name.setTypeface(current ? AndroidUtilities.bold() : android.graphics.Typeface.DEFAULT);
            String line;
            if (HumoyAccounts.getCustomName(account) != null) {
                line = UserObject.getUserName(user);
            } else if (user != null && !TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                line = "@" + UserObject.getPublicUsername(user);
            } else if (user != null && !TextUtils.isEmpty(user.phone)) {
                line = "+" + user.phone;
            } else {
                line = "";
            }
            subtitle.setText(current ? (line.isEmpty() ? "" : line + " · ") + HumoyStrings.get("AccountCurrent") : line);
            int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
            counter.setText(unread > 99999 ? "99K+" : String.format(java.util.Locale.US, "%,d", unread));
            counter.setVisibility(unread > 0 ? VISIBLE : GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(68), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (divider) {
                canvas.drawLine(dp(88), getMeasuredHeight() - 1, getMeasuredWidth() - dp(12), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class AddCell extends FrameLayout {
        AddCell(Context context) {
            super(context);
            setBackground(Theme.getSelectorDrawable(false));
            int blue = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.msg_add);
            icon.setColorFilter(blue);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 26, 0, 0, 0));
            TextView text = new TextView(context);
            text.setText(HumoyStrings.get("AccountAdd"));
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            text.setTextColor(blue);
            addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 88, 0, 16, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private boolean canAdd() {
            return !hiddenMode && HumoyAccounts.allAccounts().size() < UserConfig.MAX_ACCOUNT_COUNT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_ACCOUNT || type == VIEW_ADD;
        }

        @Override
        public int getItemCount() {
            if (hiddenMode && accounts.isEmpty()) {
                return 1;
            }
            return accounts.size() + (canAdd() ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            if (hiddenMode && accounts.isEmpty()) {
                return VIEW_EMPTY;
            }
            if (position < accounts.size()) {
                return VIEW_ACCOUNT;
            }
            return VIEW_ADD;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_ACCOUNT) {
                view = new AccountCell(parent.getContext());
            } else if (viewType == VIEW_ADD) {
                view = new AddCell(parent.getContext());
            } else {
                TextView text = new TextView(parent.getContext());
                text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                text.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                text.setLineSpacing(dp(2), 1f);
                text.setPadding(dp(28), dp(viewType == VIEW_EMPTY ? 40 : 12), dp(28), dp(12));
                if (viewType == VIEW_EMPTY) {
                    text.setGravity(Gravity.CENTER);
                }
                text.setText(HumoyStrings.get("HiddenAccountsEmpty"));
                view = text;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof AccountCell) {
                boolean divider = position < accounts.size() - 1 || canAdd() && position == accounts.size() - 1;
                ((AccountCell) holder.itemView).set(accounts.get(position), divider);
            }
        }
    }
}
