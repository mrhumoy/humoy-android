/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.view.View;

import org.telegram.messenger.HumoyConfig;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ghost mode settings: a master switch with a collapsible list of what to hide, like Telegram's permission groups. */
public class HumoyGhostModeActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int SWITCH_GHOST = 1;
    private static final int CHECK_READ_MESSAGES = 2;
    private static final int CHECK_READ_STORIES = 3;
    private static final int CHECK_ONLINE = 4;
    private static final int CHECK_TYPING = 5;
    private static final int CHECK_GO_OFFLINE = 6;
    private static final int SWITCH_READ_ON_INTERACT = 7;
    private static final int SWITCH_SHOW_BUTTON = 8;
    private static final int SWITCH_ASK_BEFORE_STORY = 9;

    private boolean expanded = true;

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
        return HumoyStrings.get("GhostMode");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(HumoyStrings.get("GhostModeHeader")));
        items.add(
            UItem.asExpandableSwitch(SWITCH_GHOST, HumoyStrings.get("GhostMode"), HumoyConfig.ghostOptionsEnabledCount() + "/" + HumoyConfig.GHOST_OPTIONS_COUNT)
                .setChecked(HumoyConfig.ghostMode)
                .setCollapsed(!expanded)
                .setClickCallback(v -> HumoyConfig.toggleGhostMode())
        );
        if (expanded) {
            items.add(UItem.asRoundCheckbox(CHECK_READ_MESSAGES, HumoyStrings.get("GhostDontReadMessages")).setChecked(HumoyConfig.ghostDontReadMessages).setPad(1));
            items.add(UItem.asRoundCheckbox(CHECK_READ_STORIES, HumoyStrings.get("GhostDontReadStories")).setChecked(HumoyConfig.ghostDontReadStories).setPad(1));
            items.add(UItem.asRoundCheckbox(CHECK_ONLINE, HumoyStrings.get("GhostDontSendOnline")).setChecked(HumoyConfig.ghostDontSendOnline).setPad(1));
            items.add(UItem.asRoundCheckbox(CHECK_TYPING, HumoyStrings.get("GhostDontSendTyping")).setChecked(HumoyConfig.ghostDontSendTyping).setPad(1));
            items.add(UItem.asRoundCheckbox(CHECK_GO_OFFLINE, HumoyStrings.get("GhostGoOffline")).setChecked(HumoyConfig.ghostGoOffline).setPad(1));
        }
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(HumoyStrings.get("GhostModeInfo")));

        items.add(UItem.asCheck(SWITCH_READ_ON_INTERACT, HumoyStrings.get("GhostReadOnInteract")).setChecked(HumoyConfig.ghostReadOnInteract));
        items.add(UItem.asShadow(HumoyStrings.get("GhostReadOnInteractInfo")));

        items.add(UItem.asCheck(SWITCH_SHOW_BUTTON, HumoyStrings.get("ShowGhostButton")).setChecked(HumoyConfig.showGhostButton));
        items.add(UItem.asShadow(HumoyStrings.get("ShowGhostButtonInfo")));
        items.add(UItem.asCheck(SWITCH_ASK_BEFORE_STORY, HumoyStrings.get("AskGhostBeforeStory")).setChecked(HumoyConfig.askGhostBeforeStory));
        items.add(UItem.asShadow(HumoyStrings.get("AskGhostBeforeStoryInfo")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case SWITCH_GHOST:
                ((TextCheckCell2) view).setChecked(expanded = !expanded);
                listView.adapter.update(true);
                return;
            case CHECK_READ_MESSAGES:
                ((CheckBoxCell) view).setChecked(HumoyConfig.ghostDontReadMessages = !HumoyConfig.ghostDontReadMessages, true);
                break;
            case CHECK_READ_STORIES:
                ((CheckBoxCell) view).setChecked(HumoyConfig.ghostDontReadStories = !HumoyConfig.ghostDontReadStories, true);
                break;
            case CHECK_ONLINE:
                ((CheckBoxCell) view).setChecked(HumoyConfig.ghostDontSendOnline = !HumoyConfig.ghostDontSendOnline, true);
                break;
            case CHECK_TYPING:
                ((CheckBoxCell) view).setChecked(HumoyConfig.ghostDontSendTyping = !HumoyConfig.ghostDontSendTyping, true);
                break;
            case CHECK_GO_OFFLINE:
                ((CheckBoxCell) view).setChecked(HumoyConfig.ghostGoOffline = !HumoyConfig.ghostGoOffline, true);
                break;
            case SWITCH_READ_ON_INTERACT:
                ((TextCheckCell) view).setChecked(HumoyConfig.ghostReadOnInteract = !HumoyConfig.ghostReadOnInteract);
                break;
            case SWITCH_SHOW_BUTTON:
                ((TextCheckCell) view).setChecked(HumoyConfig.showGhostButton = !HumoyConfig.showGhostButton);
                break;
            case SWITCH_ASK_BEFORE_STORY:
                ((TextCheckCell) view).setChecked(HumoyConfig.askGhostBeforeStory = !HumoyConfig.askGhostBeforeStory);
                break;
            default:
                return;
        }
        HumoyConfig.applyGhostOptions();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
