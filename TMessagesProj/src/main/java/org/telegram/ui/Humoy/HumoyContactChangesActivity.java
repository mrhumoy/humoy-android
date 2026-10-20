/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HumoyConfig;
import org.telegram.messenger.HumoyContactChanges;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "Contact changes": people whose name, username, phone or photo changed (with unseen counts),
 * one person's history, or everyone's changes in time order, as Telegraph shows them.
 */
public class HumoyContactChangesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MODE_PEOPLE = 0;
    private static final int MODE_ALL = 1;
    private static final int MODE_PERSON = 2;

    private static final int MENU_FILTER = 2;
    private static final int MENU_CLEAR = 3;
    private static final int MENU_SETTINGS = 4;
    private static final int MENU_PROFILE = 5;

    // each kind's icon on a gradient circle (top and bottom colours), in the style of Telegram's settings icons
    private static final int[][] TYPE_COLORS = {
            {0xFF1CA5ED, 0xFF1488E1}, // photo
            {0xFFF45255, 0xFFDF3955}, // photo removed
            {0xFF32C0CE, 0xFF1D9CC6}, // phone
            {0xFF55CA47, 0xFF27B434}, // name
            {0xFF4F85F6, 0xFF3568E8}, // username
            {0xFFF28B31, 0xFFE26314}, // blocked
            {0xFF8699AA, 0xFF6E8397}, // unblocked
    };
    private static final int[] TYPE_ICONS = {R.drawable.msg_openprofile, R.drawable.msg_user_remove, R.drawable.msg_newphone, R.drawable.msg_contacts_name, R.drawable.msg_mention, R.drawable.msg_block, R.drawable.msg_block};
    private static final String[] TYPE_KEYS = {"CcPhoto", "CcPhotoRemoved", "CcPhone", "CcName", "CcUsername", "CcBlocked", "CcUnblocked"};

    private int mode;
    private final long userId;
    private int filter = HumoyContactChanges.ALL_TYPES;

    private RecyclerListView listView;
    private Adapter adapter;
    private LinearLayout emptyView;
    private ActionBarMenuItem filterItem;
    private final ArrayList<HumoyContactChanges.Person> people = new ArrayList<>();
    private final ArrayList<HumoyContactChanges.Change> changes = new ArrayList<>();
    private boolean loaded;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy HH:mm", Locale.US);

    public HumoyContactChangesActivity() {
        this.mode = HumoyConfig.contactChangesView == 1 ? MODE_ALL : MODE_PEOPLE;
        this.userId = 0;
    }

    private HumoyContactChangesActivity(long userId) {
        this.mode = MODE_PERSON;
        this.userId = userId;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.humoyContactChanged);
        reload();
        if (mode == MODE_PERSON) {
            HumoyContactChanges.getInstance(currentAccount).markSeen(userId);
        } else if (mode == MODE_ALL) {
            HumoyContactChanges.getInstance(currentAccount).markSeen(0);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.humoyContactChanged);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.humoyContactChanged) {
            reload();
        }
    }

    private void reload() {
        HumoyContactChanges store = HumoyContactChanges.getInstance(currentAccount);
        if (mode == MODE_PEOPLE) {
            store.loadPeople(list -> {
                people.clear();
                people.addAll(list);
                loaded = true;
                updateViews();
                loadMissingUsers();
            });
        } else {
            store.loadChanges(mode == MODE_PERSON ? userId : 0, filter, list -> {
                changes.clear();
                changes.addAll(list);
                loaded = true;
                updateViews();
            });
        }
    }

    /** People not in memory right now are read from the device cache, so they show with name and photo. */
    private void loadMissingUsers() {
        ArrayList<Long> missing = new ArrayList<>();
        for (HumoyContactChanges.Person p : people) {
            if (getMessagesController().getUser(p.userId) == null) {
                missing.add(p.userId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.User> users = getMessagesStorage().getUsers(missing);
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, true);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    private void updateViews() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            boolean empty = mode == MODE_PEOPLE ? people.isEmpty() : changes.isEmpty();
            emptyView.setVisibility(loaded && empty ? View.VISIBLE : View.GONE);
        }
        if (filterItem != null) {
            filterItem.setVisibility(mode == MODE_PEOPLE ? View.GONE : View.VISIBLE);
            filterItem.setIconColor(getThemedColor(filter != HumoyContactChanges.ALL_TYPES ? Theme.key_featuredStickers_addButton : Theme.key_actionBarDefaultIcon));
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (mode == MODE_PERSON) {
            TLRPC.User user = getMessagesController().getUser(userId);
            actionBar.setTitle(user != null ? UserObject.getUserName(user) : String.valueOf(userId));
            actionBar.setSubtitle(HumoyStrings.get("ContactChanges"));
        } else {
            actionBar.setTitle(HumoyStrings.get("ContactChanges"));
        }
        ActionBarMenu menu = actionBar.createMenu();
        filterItem = menu.addItem(MENU_FILTER, R.drawable.msg_list);
        filterItem.setIcon(R.drawable.menu_tag_filter);
        filterItem.setContentDescription(HumoyStrings.get("CcFilter"));
        ActionBarMenuItem more = menu.addItem(0, R.drawable.ic_ab_other);
        if (mode == MODE_PERSON) {
            more.addSubItem(MENU_PROFILE, R.drawable.msg_openprofile, HumoyStrings.get("FinderProfile"));
            more.addSubItem(MENU_CLEAR, R.drawable.msg_delete, HumoyStrings.get("CcClearPerson"));
        } else {
            more.addSubItem(MENU_CLEAR, R.drawable.msg_delete, HumoyStrings.get("CcClearAll"));
            more.addSubItem(MENU_SETTINGS, R.drawable.msg_settings, HumoyStrings.get("CcSettings"));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_FILTER) {
                    showFilter();
                } else if (id == MENU_CLEAR) {
                    confirmClear();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new HumoyContactChangesSettingsActivity());
                } else if (id == MENU_PROFILE) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", userId);
                    presentFragment(new ProfileActivity(args));
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setVerticalScrollBarEnabled(true);
        listView.setPadding(0, dp(8), 0, dp(8));
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> onItemClick(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        // Telegram's own duck, looping, like the sticker Telegraph shows here
        RLottieImageView emptyIcon = new RLottieImageView(context);
        emptyIcon.setAutoRepeat(true);
        emptyIcon.setAnimation(R.raw.utyan_empty, 130, 130);
        emptyIcon.playAnimation();
        emptyView.addView(emptyIcon, LayoutHelper.createLinear(130, 130));
        TextView emptyTitle = new TextView(context);
        emptyTitle.setText(HumoyStrings.get("CcEmpty"));
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        emptyTitle.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));
        TextView emptyText = new TextView(context);
        emptyText.setText(HumoyStrings.get(HumoyConfig.contactChangesScope == HumoyContactChanges.SCOPE_NONE ? "CcEmptyOff" : "CcEmptyInfo"));
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(dp(2), 1f);
        emptyView.addView(emptyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 40, 8, 40, 0));
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 0, 0, 0, 60));

        updateViews();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void onItemClick(int position) {
        if (mode == MODE_PEOPLE) {
            if (position >= 0 && position < people.size()) {
                presentFragment(new HumoyContactChangesActivity(people.get(position).userId));
            }
            return;
        }
        if (position < 0 || position >= changes.size()) {
            return;
        }
        HumoyContactChanges.Change change = changes.get(position);
        if (change.photoPath != null && new java.io.File(change.photoPath).exists()) {
            openPhoto(change, position);
        } else if (mode == MODE_ALL) {
            presentFragment(new HumoyContactChangesActivity(change.userId));
        } else {
            Bundle args = new Bundle();
            args.putLong("user_id", change.userId);
            presentFragment(new ProfileActivity(args));
        }
    }

    private void openPhoto(HumoyContactChanges.Change change, int position) {
        ArrayList<Object> photos = new ArrayList<>();
        photos.add(new MediaController.PhotoEntry(0, 0, change.date * 1000L, change.photoPath, 0, false, 0, 0, 0));
        PhotoViewer.getInstance().setParentActivity(this);
        PhotoViewer.getInstance().openPhotoForSelect(photos, 0, PhotoViewer.SELECT_TYPE_NO_SELECT, false, new PhotoViewer.EmptyPhotoViewerProvider(), null);
    }

    private void showFilter() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, dp(8));

        TextView title = new TextView(context);
        title.setText(HumoyStrings.get("CcFilter"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        title.setPadding(dp(21), dp(18), dp(21), dp(10));
        layout.addView(title);

        BottomSheet[] sheet = new BottomSheet[1];
        for (int i = 0; i <= HumoyContactChanges.TYPES_COUNT; i++) {
            int value = i == 0 ? HumoyContactChanges.ALL_TYPES : 1 << (i - 1);
            FrameLayout row = new FrameLayout(context);
            row.setBackground(Theme.getSelectorDrawable(false));

            org.telegram.ui.Components.RadioButton radio = new org.telegram.ui.Components.RadioButton(context);
            radio.setSize(dp(20));
            radio.setColor(getThemedColor(Theme.key_radioBackground), getThemedColor(Theme.key_featuredStickers_addButton));
            radio.setChecked(filter == value, false);
            row.addView(radio, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 21, 0, 0, 0));

            ImageView icon = new ImageView(context);
            icon.setImageResource(i == 0 ? R.drawable.msg_list : TYPE_ICONS[i - 1]);
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_dialogIcon), android.graphics.PorterDuff.Mode.SRC_IN));
            icon.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 62, 0, 0, 0));

            TextView text = new TextView(context);
            text.setText(i == 0 ? HumoyStrings.get("CcAll") : HumoyStrings.get(TYPE_KEYS[i - 1]));
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            text.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 110, 0, 21, 0));

            row.setOnClickListener(v -> {
                filter = value;
                reload();
                updateViews();
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
            });
            layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        }
        builder.setCustomView(layout);
        sheet[0] = builder.create();
        showDialog(sheet[0]);
    }

    private void confirmClear() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(HumoyStrings.get(mode == MODE_PERSON ? "CcClearPerson" : "CcClearAll"));
        builder.setMessage(HumoyStrings.get(mode == MODE_PERSON ? "CcClearPersonConfirm" : "CcClearAllConfirm"));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
            HumoyContactChanges.getInstance(currentAccount).clear(mode == MODE_PERSON ? userId : 0);
            if (mode == MODE_PERSON) {
                finishFragment();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    // ---- texts

    private String describe(HumoyContactChanges.Change change) {
        switch (change.type) {
            case HumoyContactChanges.TYPE_PHOTO:
                return HumoyStrings.get(change.photoPath != null ? "CcPhotoOldSaved" : "CcPhotoNew");
            case HumoyContactChanges.TYPE_PHOTO_REMOVED:
                return HumoyStrings.get(change.photoPath != null ? "CcPhotoOldSaved" : "CcPhotoRemovedInfo");
            case HumoyContactChanges.TYPE_BLOCKED:
                return HumoyStrings.get("CcBlockedInfo");
            case HumoyContactChanges.TYPE_UNBLOCKED:
                return HumoyStrings.get("CcUnblockedInfo");
            default:
                String oldValue = shown(change.type, change.oldValue);
                String newValue = shown(change.type, change.newValue);
                return oldValue + "  →  " + newValue;
        }
    }

    private static String shown(int type, String value) {
        if (TextUtils.isEmpty(value)) {
            return type == HumoyContactChanges.TYPE_USERNAME ? HumoyStrings.get("CcNoUsername") : "—";
        }
        if (type == HumoyContactChanges.TYPE_PHONE) {
            String digits = PhoneFormat.stripExceptNumbers(value);
            if (digits.startsWith("998") && digits.length() == 12) {
                return "+998 " + digits.substring(3, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8, 10) + " " + digits.substring(10);
            }
            return PhoneFormat.getInstance().format("+" + digits);
        }
        return value;
    }

    private static android.graphics.drawable.Drawable circle(int[] colors) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors);
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        return drawable;
    }

    // ---- cells

    private class PersonCell extends FrameLayout {

        private final BackupImageView avatar;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView name;
        private final TextView count;
        private final ImageView contactIcon;
        private final TextView badge;
        private boolean divider;

        PersonCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

            avatar = new BackupImageView(context);
            avatar.setRoundRadius(dp(24));
            addView(avatar, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            count = new TextView(context);
            count.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            count.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            texts.addView(count, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 78, 0, 96, 0));

            contactIcon = new ImageView(context);
            contactIcon.setImageResource(R.drawable.msg_contacts);
            contactIcon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon));
            contactIcon.setScaleType(ImageView.ScaleType.CENTER);
            addView(contactIcon, LayoutHelper.createFrame(28, 28, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 52, 0));

            badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(0xffffffff);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(26));
            badge.setPadding(dp(7), 0, dp(7), 0);
            badge.setBackground(Theme.createRoundRectDrawable(dp(13), 0xFFEF5350));
            addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        }

        void set(HumoyContactChanges.Person person, boolean divider) {
            this.divider = divider;
            TLRPC.User user = getMessagesController().getUser(person.userId);
            avatarDrawable.setInfo(currentAccount, user);
            avatar.setForUserOrChat(user, avatarDrawable);
            name.setText(user != null ? UserObject.getUserName(user) : String.valueOf(person.userId));
            count.setText(String.format(HumoyStrings.get("CcCount"), person.count));
            contactIcon.setVisibility(user != null && user.contact ? VISIBLE : GONE);
            badge.setText(String.valueOf(person.unseen));
            badge.setVisibility(person.unseen > 0 ? VISIBLE : GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(72), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (divider) {
                canvas.drawLine(dp(78), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class ChangeCell extends FrameLayout {

        private final ImageView typeIcon;
        private final BackupImageView photo;
        private final TextView name;
        private final TextView info;
        private final TextView value;
        private boolean divider;

        ChangeCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

            typeIcon = new ImageView(context);
            typeIcon.setScaleType(ImageView.ScaleType.CENTER);
            typeIcon.setColorFilter(0xffffffff);
            addView(typeIcon, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 16, 14, 0, 0));
            photo = new BackupImageView(context);
            photo.setRoundRadius(dp(24));
            addView(photo, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 16, 14, 0, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            info = new TextView(context);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            info.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
            texts.addView(info, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            value = new TextView(context);
            value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            value.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            value.setLineSpacing(dp(2), 1f);
            texts.addView(value, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 78, 12, 16, 14));
        }

        void set(HumoyContactChanges.Change change, boolean divider) {
            this.divider = divider;
            boolean hasPhoto = change.photoPath != null && new java.io.File(change.photoPath).exists();
            if (hasPhoto) {
                photo.setVisibility(VISIBLE);
                typeIcon.setVisibility(GONE);
                photo.setImage(change.photoPath, "48_48", null);
            } else {
                photo.setVisibility(GONE);
                typeIcon.setVisibility(VISIBLE);
                typeIcon.setImageResource(TYPE_ICONS[change.type]);
                typeIcon.setBackground(circle(TYPE_COLORS[change.type]));
            }
            TLRPC.User user = getMessagesController().getUser(change.userId);
            name.setText(user != null ? UserObject.getUserName(user) : String.valueOf(change.userId));
            info.setText(HumoyStrings.get(TYPE_KEYS[change.type]) + "  ·  " + dateFormat.format(new Date(change.date * 1000L)));
            value.setText(describe(change));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (divider) {
                canvas.drawLine(dp(78), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return mode == MODE_PEOPLE ? people.size() : changes.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mode == MODE_PEOPLE ? 0 : 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = viewType == 0 ? new PersonCell(parent.getContext()) : new ChangeCell(parent.getContext());
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            boolean divider = position < getItemCount() - 1;
            if (holder.itemView instanceof PersonCell) {
                ((PersonCell) holder.itemView).set(people.get(position), divider);
            } else {
                ((ChangeCell) holder.itemView).set(changes.get(position), divider);
            }
        }
    }
}
