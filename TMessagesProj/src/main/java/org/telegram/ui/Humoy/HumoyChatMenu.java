/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.HumoyConfig;
import org.telegram.messenger.HumoyHiddenChats;
import org.telegram.messenger.HumoyLockedChats;
import org.telegram.messenger.HumoyPin;
import org.telegram.messenger.HumoyMessageStore;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

/** The "Humoy" page of a chat's ⋮ menu: deleted messages and this chat's ghost mode exceptions. */
public class HumoyChatMenu {

    public static View create(Context context, ChatActivity chatActivity, ActionBarMenuItem headerItem, long dialogId) {
        Theme.ResourcesProvider resourcesProvider = chatActivity.getResourceProvider();
        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider);
        layout.setFitItems(true);
        PopupSwipeBackLayout swipeBack = headerItem.getPopupLayout().getSwipeBack();

        ActionBarMenuSubItem back = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, resourcesProvider);
        back.setOnClickListener(v -> swipeBack.closeForeground());
        addGap(context, layout, resourcesProvider);

        ActionBarMenuSubItem viewDeleted = ActionBarMenuItem.addItem(layout, R.drawable.msg_archive, HumoyStrings.get("ViewDeleted"), false, resourcesProvider);
        viewDeleted.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            chatActivity.presentFragment(HumoyMessagesActivity.deletedMessages(dialogId, resourcesProvider,
                    chatActivity.getHumoyWallpaper(), chatActivity.getHumoyWallpaperMotion()));
        });

        ActionBarMenuSubItem readExclusion = ActionBarMenuItem.addItem(layout, R.drawable.msg_views, HumoyStrings.get("ReadExclusion"), false, resourcesProvider);
        readExclusion.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            showExclusionPicker(chatActivity, HumoyStrings.get("ReadExclusionTitle"), HumoyConfig.getReadExclusion(dialogId),
                    new String[]{HumoyStrings.get("ExclusionDefault"), HumoyStrings.get("ReadNever"), HumoyStrings.get("ReadAlways")},
                    which -> HumoyConfig.setReadExclusion(dialogId, which));
        });

        ActionBarMenuSubItem typingExclusion = ActionBarMenuItem.addItem(layout, R.drawable.msg_edit, HumoyStrings.get("TypingExclusion"), false, resourcesProvider);
        typingExclusion.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            showExclusionPicker(chatActivity, HumoyStrings.get("TypingExclusionTitle"), HumoyConfig.getTypingExclusion(dialogId),
                    new String[]{HumoyStrings.get("ExclusionDefault"), HumoyStrings.get("TypingNever"), HumoyStrings.get("TypingAlways")},
                    which -> HumoyConfig.setTypingExclusion(dialogId, which));
        });
        addGap(context, layout, resourcesProvider);

        int account = chatActivity.getCurrentAccount();
        boolean locked = HumoyLockedChats.isLocked(account, dialogId);
        ActionBarMenuSubItem lock = ActionBarMenuItem.addItem(layout, locked ? R.drawable.menu_unlock : R.drawable.msg_secret, HumoyStrings.get(locked ? "ChatUnlock" : "ChatLock"), false, resourcesProvider);
        lock.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            Runnable apply = () -> {
                HumoyLockedChats.setLocked(account, dialogId, !locked);
                BulletinFactory.of(chatActivity).createSimpleBulletin(locked ? R.raw.contact_check : R.raw.passcode_lock_close, HumoyStrings.get(locked ? "ChatUnlocked" : "ChatLocked")).show();
            };
            if (HumoyPin.hasPin(HumoyPin.SCOPE_LOCKED)) {
                apply.run();
            } else {
                chatActivity.presentFragment(new HumoyPinActivity(HumoyPin.SCOPE_LOCKED, HumoyPinActivity.MODE_CREATE, apply));
            }
        });

        boolean hidden = HumoyHiddenChats.isHidden(account, dialogId);
        ActionBarMenuSubItem hide = ActionBarMenuItem.addItem(layout, hidden ? R.drawable.msg_viewintopic : R.drawable.menu_hide_gift, HumoyStrings.get(hidden ? "ChatUnhide" : "ChatHide"), false, resourcesProvider);
        hide.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            Runnable apply = () -> {
                HumoyHiddenChats.setHidden(account, java.util.Collections.singletonList(dialogId), !hidden);
                BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.contact_check, HumoyStrings.get(hidden ? "ChatUnhiddenDone" : "ChatHiddenDone")).show();
            };
            if (hidden || HumoyPin.hasPin(HumoyPin.SCOPE_HIDDEN)) {
                apply.run();
            } else {
                chatActivity.presentFragment(new HumoyPinActivity(HumoyPin.SCOPE_HIDDEN, HumoyPinActivity.MODE_CREATE, apply));
            }
        });
        addGap(context, layout, resourcesProvider);

        ActionBarMenuSubItem clearDeleted = ActionBarMenuItem.addItem(layout, R.drawable.msg_clear, HumoyStrings.get("ClearDeleted"), false, resourcesProvider);
        int red = Theme.getColor(Theme.key_text_RedRegular, resourcesProvider);
        clearDeleted.setColors(red, red);
        clearDeleted.setOnClickListener(v -> {
            headerItem.toggleSubMenu();
            confirmClearDeleted(chatActivity, dialogId);
        });
        return layout;
    }

    private static void addGap(Context context, ActionBarPopupWindow.ActionBarPopupWindowLayout layout, Theme.ResourcesProvider resourcesProvider) {
        FrameLayout gap = new FrameLayout(context);
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
        layout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
    }

    private interface Choice {
        void run(int which);
    }

    private static void showExclusionPicker(ChatActivity chatActivity, String title, int selected, String[] options, Choice choice) {
        if (chatActivity.getParentActivity() == null) {
            return;
        }
        chatActivity.showDialog(AlertsCreator.createSingleChoiceDialog(chatActivity.getParentActivity(), options, title, selected, (dialog, which) -> choice.run(which)));
    }

    private static void confirmClearDeleted(ChatActivity chatActivity, long dialogId) {
        if (chatActivity.getParentActivity() == null) {
            return;
        }
        HumoyMessageStore store = HumoyMessageStore.getInstance(chatActivity.getCurrentAccount());
        int count = store.getDeletedCount(dialogId);
        if (count == 0) {
            BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, HumoyStrings.get("NoDeletedMessages")).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(chatActivity.getParentActivity(), chatActivity.getResourceProvider())
                .setTitle(HumoyStrings.get("ClearDeleted"))
                .setMessage(String.format(HumoyStrings.get("ClearDeletedConfirm"), count))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> store.clearDeleted(dialogId))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        chatActivity.showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof android.widget.TextView) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold, chatActivity.getResourceProvider()));
        }
    }
}
