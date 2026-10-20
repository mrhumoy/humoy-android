/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.HumoyConfig;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * "Humoy Settings": the main page shows the app with its sections and links, as AyuGram does;
 * the Saving and Sending sections open as pages of this same screen.
 */
public class HumoySettingsActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int PAGE_MAIN = 0;
    public static final int PAGE_SAVING = 1;
    public static final int PAGE_SENDING = 2;

    public static final String CHANNEL = "humoymessenger";
    private static final String SOURCE_CODE = "https://github.com/mrhumoy/humoy-android";

    private static final int BUTTON_GHOST = 1;
    private static final int BUTTON_FOLDERS = 2;
    private static final int CHECK_CONFIRM_STICKER = 3;
    private static final int CHECK_CONFIRM_VOICE = 4;
    private static final int CHECK_CONFIRM_GIF = 5;
    private static final int CHECK_KEEP_DELETED = 6;
    private static final int CHECK_SAVE_EDITS = 7;
    private static final int CHECK_SAVE_ANY_STORY = 8;
    private static final int CHECK_SAVE_PROTECTED = 9;
    private static final int CHECK_SAVE_TTL = 10;
    private static final int CHECK_VIEW_TTL_SILENTLY = 11;
    private static final int BUTTON_SAVED_TTL = 12;
    private static final int CHECK_AUTO_MARK_TTL = 13;
    private static final int BUTTON_SAVING = 14;
    private static final int BUTTON_SENDING = 15;
    private static final int LINK_CHANNEL = 16;
    private static final int LINK_SOURCE = 17;
    private static final int CHECK_KEEP_STORY_VIEWERS = 20;
    private static final int BUTTON_PRIVACY = 21;
    private static final int LINK_PRIVACY_POLICY = 22;

    private final int page;
    private View headerView;

    public HumoySettingsActivity() {
        this(PAGE_MAIN);
    }

    public HumoySettingsActivity(int page) {
        this.page = page;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.humoyGhostModeChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.humoyGhostModeChanged);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.humoyGhostModeChanged && listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected CharSequence getTitle() {
        if (page == PAGE_SAVING) {
            return HumoyStrings.get("SectionSaving");
        } else if (page == PAGE_SENDING) {
            return HumoyStrings.get("SectionSending");
        }
        return HumoyStrings.get("HumoySettings");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (page == PAGE_SAVING) {
            fillSaving(items);
        } else if (page == PAGE_SENDING) {
            fillSending(items);
        } else {
            fillMain(items);
        }
    }

    private void fillMain(ArrayList<UItem> items) {
        if (headerView == null) {
            headerView = createHeader(getContext());
        }
        items.add(UItem.asCustom(headerView));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(HumoyStrings.get("HumoySections")));
        items.add(UItem.asButton(BUTTON_GHOST, R.drawable.msg2_ghost,
                HumoyStrings.get("GhostMode"), HumoyStrings.get(HumoyConfig.ghostMode ? "GhostStateOn" : "GhostStateOff")));
        items.add(UItem.asButton(BUTTON_SAVING, R.drawable.msg_saved, HumoyStrings.get("SectionSaving")));
        items.add(UItem.asButton(BUTTON_FOLDERS, R.drawable.msg2_folder, HumoyStrings.get("FolderTabs")));
        items.add(UItem.asButton(BUTTON_SENDING, R.drawable.msg_select, HumoyStrings.get("SectionSending")));
        items.add(UItem.asButton(BUTTON_PRIVACY, R.drawable.msg_secret, HumoyStrings.get("HumoyPrivacy")));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(HumoyStrings.get("HumoyLinks")));
        items.add(UItem.asButton(LINK_CHANNEL, R.drawable.msg_channel, HumoyStrings.get("HumoyChannel"), "@" + CHANNEL));
        items.add(UItem.asButton(LINK_SOURCE, R.drawable.msg_link2, HumoyStrings.get("HumoySourceCode"), "GitHub"));
        items.add(UItem.asButton(LINK_PRIVACY_POLICY, R.drawable.msg2_policy, HumoyStrings.get("HumoyPrivacyPolicy")));
        items.add(UItem.asShadow(null));
    }

    private void fillSaving(ArrayList<UItem> items) {
        items.add(UItem.asHeader(HumoyStrings.get("SavedMessagesHeader")));
        items.add(UItem.asCheck(CHECK_KEEP_DELETED, HumoyStrings.get("KeepDeletedMessages")).setChecked(HumoyConfig.keepDeletedMessages));
        items.add(UItem.asCheck(CHECK_SAVE_EDITS, HumoyStrings.get("SaveEditHistory")).setChecked(HumoyConfig.saveEditHistory));
        items.add(UItem.asShadow(HumoyStrings.get("SavedMessagesInfo")));

        boolean play = org.telegram.messenger.ApplicationLoader.isPlayStoreBuild();
        if (!play) {
            items.add(UItem.asHeader(HumoyStrings.get("SelfDestructHeader")));
            items.add(UItem.asCheck(CHECK_SAVE_TTL, HumoyStrings.get("SaveSelfDestructMedia")).setChecked(HumoyConfig.saveSelfDestructMedia));
            items.add(UItem.asCheck(CHECK_VIEW_TTL_SILENTLY, HumoyStrings.get("ViewSelfDestructSilently")).setChecked(HumoyConfig.viewSelfDestructSilently));
            items.add(UItem.asButton(BUTTON_SAVED_TTL, R.drawable.msg_autodelete, HumoyStrings.get("SavedSelfDestructMedia")));
            items.add(UItem.asShadow(HumoyStrings.get("SelfDestructInfo")));
            if (HumoyConfig.viewSelfDestructSilently) {
                items.add(UItem.asCheck(CHECK_AUTO_MARK_TTL, HumoyStrings.get("AutoMarkSelfDestructViewed")).setChecked(HumoyConfig.autoMarkSelfDestructViewed));
                items.add(UItem.asShadow(HumoyStrings.get("AutoMarkSelfDestructViewedInfo")));
            }
        }

        items.add(UItem.asHeader(HumoyStrings.get("StoriesHeader")));
        items.add(UItem.asCheck(CHECK_SAVE_ANY_STORY, HumoyStrings.get("SaveAnyStory")).setChecked(HumoyConfig.saveAnyStory));
        items.add(UItem.asShadow(HumoyStrings.get("SaveAnyStoryInfo")));
        items.add(UItem.asCheck(CHECK_KEEP_STORY_VIEWERS, HumoyStrings.get("KeepStoryViewers")).setChecked(HumoyConfig.keepStoryViewers));
        items.add(UItem.asShadow(HumoyStrings.get("KeepStoryViewersInfo")));

        if (!play) {
            items.add(UItem.asHeader(HumoyStrings.get("ProtectedHeader")));
            items.add(UItem.asCheck(CHECK_SAVE_PROTECTED, HumoyStrings.get("SaveProtectedContent")).setChecked(HumoyConfig.saveProtectedContent));
            items.add(UItem.asShadow(HumoyStrings.get("SaveProtectedContentInfo")));
        }
    }

    private void fillSending(ArrayList<UItem> items) {
        items.add(UItem.asHeader(HumoyStrings.get("ConfirmHeader")));
        items.add(UItem.asCheck(CHECK_CONFIRM_STICKER, HumoyStrings.get("ConfirmSticker")).setChecked(HumoyConfig.confirmSticker));
        items.add(UItem.asCheck(CHECK_CONFIRM_VOICE, HumoyStrings.get("ConfirmVoice")).setChecked(HumoyConfig.confirmVoice));
        items.add(UItem.asCheck(CHECK_CONFIRM_GIF, HumoyStrings.get("ConfirmGif")).setChecked(HumoyConfig.confirmGif));
        items.add(UItem.asShadow(HumoyStrings.get("ConfirmInfo")));
    }

    /** Logo, name with version and a line about the app, at the top of the main page. */
    private View createHeader(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.humoy_logo_round);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(logo, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(context);
        title.setText("Humoy " + HumoyConfig.VERSION);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        TextView basedOn = new TextView(context);
        basedOn.setText(String.format(HumoyStrings.get("HumoyBasedOn"), BuildVars.BUILD_VERSION_STRING));
        basedOn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        basedOn.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        basedOn.setGravity(Gravity.CENTER);
        layout.addView(basedOn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

        TextView description = new TextView(context);
        description.setText(HumoyStrings.get("HumoyAbout"));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        layout.addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        return layout;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_GHOST) {
            presentFragment(new HumoyGhostModeActivity());
        } else if (item.id == BUTTON_FOLDERS) {
            presentFragment(new HumoyFoldersActivity());
        } else if (item.id == BUTTON_SAVING) {
            presentFragment(new HumoySettingsActivity(PAGE_SAVING));
        } else if (item.id == BUTTON_SENDING) {
            presentFragment(new HumoySettingsActivity(PAGE_SENDING));
        } else if (item.id == BUTTON_PRIVACY) {
            presentFragment(new HumoyPrivacyActivity());
        } else if (item.id == LINK_CHANNEL) {
            Browser.openUrl(getParentActivity(), "https://t.me/" + CHANNEL);
        } else if (item.id == LINK_PRIVACY_POLICY) {
            Browser.openUrl(getParentActivity(), org.telegram.messenger.HumoyServer.siteUrl("privacy.html"));
        } else if (item.id == LINK_SOURCE) {
            Browser.openUrl(getParentActivity(), SOURCE_CODE);
        } else if (item.id == CHECK_CONFIRM_STICKER) {
            ((TextCheckCell) view).setChecked(HumoyConfig.confirmSticker = !HumoyConfig.confirmSticker);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_CONFIRM_VOICE) {
            ((TextCheckCell) view).setChecked(HumoyConfig.confirmVoice = !HumoyConfig.confirmVoice);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_KEEP_DELETED) {
            ((TextCheckCell) view).setChecked(HumoyConfig.keepDeletedMessages = !HumoyConfig.keepDeletedMessages);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_SAVE_EDITS) {
            ((TextCheckCell) view).setChecked(HumoyConfig.saveEditHistory = !HumoyConfig.saveEditHistory);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_SAVE_ANY_STORY) {
            ((TextCheckCell) view).setChecked(HumoyConfig.saveAnyStory = !HumoyConfig.saveAnyStory);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_KEEP_STORY_VIEWERS) {
            ((TextCheckCell) view).setChecked(HumoyConfig.keepStoryViewers = !HumoyConfig.keepStoryViewers);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_SAVE_PROTECTED) {
            ((TextCheckCell) view).setChecked(HumoyConfig.saveProtectedContent = !HumoyConfig.saveProtectedContent);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_SAVE_TTL) {
            ((TextCheckCell) view).setChecked(HumoyConfig.saveSelfDestructMedia = !HumoyConfig.saveSelfDestructMedia);
            HumoyConfig.saveConfig();
        } else if (item.id == CHECK_VIEW_TTL_SILENTLY) {
            ((TextCheckCell) view).setChecked(HumoyConfig.viewSelfDestructSilently = !HumoyConfig.viewSelfDestructSilently);
            HumoyConfig.saveConfig();
            listView.adapter.update(true);
        } else if (item.id == CHECK_AUTO_MARK_TTL) {
            ((TextCheckCell) view).setChecked(HumoyConfig.autoMarkSelfDestructViewed = !HumoyConfig.autoMarkSelfDestructViewed);
            HumoyConfig.saveConfig();
        } else if (item.id == BUTTON_SAVED_TTL) {
            presentFragment(new HumoyTtlMediaActivity());
        } else if (item.id == CHECK_CONFIRM_GIF) {
            ((TextCheckCell) view).setChecked(HumoyConfig.confirmGif = !HumoyConfig.confirmGif);
            HumoyConfig.saveConfig();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
