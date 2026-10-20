/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.HumoyHiddenChats;
import org.telegram.messenger.HumoyLockedChats;
import org.telegram.messenger.HumoyPin;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/** Humoy's private places: locked chats, the hidden section and hidden accounts, each with its own PIN. */
public class HumoyPrivacyActivity extends UniversalFragment {

    private static final int LOCKED_SET = 1;
    private static final int LOCKED_CHANGE = 2;
    private static final int LOCKED_FINGER = 3;
    private static final int LOCKED_OFF = 4;
    private static final int HIDDEN_OPEN = 5;
    private static final int HIDDEN_SET = 6;
    private static final int HIDDEN_CHANGE = 7;
    private static final int HIDDEN_FINGER = 8;
    private static final int HIDDEN_NOTIFY = 9;
    private static final int HIDDEN_OFF = 10;
    private static final int ACCOUNTS_OPEN = 11;

    private static DialogsActivity hiddenList() {
        Bundle args = new Bundle();
        args.putBoolean("humoyHidden", true);
        return new DialogsActivity(args);
    }

    /** Shows the hidden section's chat list (the PIN was already given). */
    public static void presentHiddenList(BaseFragment from) {
        from.presentFragment(hiddenList());
    }

    /** Opens the hidden section after its PIN; the first time, the PIN is created first. */
    public static void openHiddenSection(BaseFragment from) {
        int mode = HumoyPin.hasPin(HumoyPin.SCOPE_HIDDEN) ? HumoyPinActivity.MODE_CHECK : HumoyPinActivity.MODE_CREATE;
        from.presentFragment(new HumoyPinActivity(HumoyPin.SCOPE_HIDDEN, mode, null).setNext(hiddenList()));
    }

    @Override
    protected CharSequence getTitle() {
        return HumoyStrings.get("HumoyPrivacy");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(HumoyStrings.get("LockedChats")));
        if (!HumoyPin.hasPin(HumoyPin.SCOPE_LOCKED)) {
            items.add(UItem.asButton(LOCKED_SET, R.drawable.msg_secret, HumoyStrings.get("PinSet")).accent());
        } else {
            items.add(UItem.asButton(LOCKED_CHANGE, R.drawable.msg_secret, HumoyStrings.get("PinChange")));
            items.add(UItem.asCheck(LOCKED_FINGER, HumoyStrings.get("UseFingerprint")).setChecked(HumoyPin.useFingerprint(HumoyPin.SCOPE_LOCKED)));
            items.add(UItem.asButton(LOCKED_OFF, R.drawable.msg_delete, HumoyStrings.get("LockedChatsOff")).red());
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(HumoyStrings.get("HiddenSection")));
        if (!HumoyPin.hasPin(HumoyPin.SCOPE_HIDDEN)) {
            items.add(UItem.asButton(HIDDEN_SET, R.drawable.msg_secret, HumoyStrings.get("PinSet")).accent());
        } else {
            items.add(UItem.asButton(HIDDEN_OPEN, R.drawable.msg_viewintopic, HumoyStrings.get("HiddenSectionOpen")));
            items.add(UItem.asButton(HIDDEN_CHANGE, R.drawable.msg_secret, HumoyStrings.get("PinChange")));
            items.add(UItem.asCheck(HIDDEN_FINGER, HumoyStrings.get("UseFingerprint")).setChecked(HumoyPin.useFingerprint(HumoyPin.SCOPE_HIDDEN)));
            items.add(UItem.asCheck(HIDDEN_NOTIFY, HumoyStrings.get("HiddenNotify")).setChecked(HumoyHiddenChats.showNotifications()));
            items.add(UItem.asButton(HIDDEN_OFF, R.drawable.msg_delete, HumoyStrings.get("HiddenSectionOff")).red());
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(HumoyStrings.get("HiddenAccounts")));
        items.add(UItem.asButton(ACCOUNTS_OPEN, R.drawable.msg_viewintopic, HumoyStrings.get("HiddenAccountsOpen")));
        items.add(UItem.asShadow(null));
    }

    private void changePin(int scope) {
        presentFragment(new HumoyPinActivity(scope, HumoyPinActivity.MODE_CHECK, () -> {
            BaseFragment last = LaunchActivity.getLastFragment();
            if (last != null) {
                last.presentFragment(new HumoyPinActivity(scope, HumoyPinActivity.MODE_CREATE, () ->
                        BulletinFactory.of(HumoyPrivacyActivity.this).createSimpleBulletin(R.raw.contact_check, HumoyStrings.get("PinChanged")).show()));
            }
        }));
    }

    private void confirmOff(String title, String message, Runnable off) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(HumoyStrings.get("TurnOff"), (d, w) -> {
                    off.run();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ((TextView) button).setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == LOCKED_SET) {
            presentFragment(new HumoyPinActivity(HumoyPin.SCOPE_LOCKED, HumoyPinActivity.MODE_CREATE, null));
        } else if (item.id == LOCKED_CHANGE) {
            changePin(HumoyPin.SCOPE_LOCKED);
        } else if (item.id == LOCKED_FINGER) {
            boolean use = !HumoyPin.useFingerprint(HumoyPin.SCOPE_LOCKED);
            HumoyPin.setUseFingerprint(HumoyPin.SCOPE_LOCKED, use);
            ((TextCheckCell) view).setChecked(use);
        } else if (item.id == LOCKED_OFF) {
            confirmOff(HumoyStrings.get("LockedChatsOff"), HumoyStrings.get("LockedChatsOffConfirm"), HumoyLockedChats::clearAll);
        } else if (item.id == HIDDEN_SET) {
            presentFragment(new HumoyPinActivity(HumoyPin.SCOPE_HIDDEN, HumoyPinActivity.MODE_CREATE, null));
        } else if (item.id == HIDDEN_OPEN) {
            openHiddenSection(this);
        } else if (item.id == HIDDEN_CHANGE) {
            changePin(HumoyPin.SCOPE_HIDDEN);
        } else if (item.id == HIDDEN_FINGER) {
            boolean use = !HumoyPin.useFingerprint(HumoyPin.SCOPE_HIDDEN);
            HumoyPin.setUseFingerprint(HumoyPin.SCOPE_HIDDEN, use);
            ((TextCheckCell) view).setChecked(use);
        } else if (item.id == HIDDEN_NOTIFY) {
            boolean show = !HumoyHiddenChats.showNotifications();
            HumoyHiddenChats.setShowNotifications(show);
            ((TextCheckCell) view).setChecked(show);
        } else if (item.id == HIDDEN_OFF) {
            confirmOff(HumoyStrings.get("HiddenSectionOff"), HumoyStrings.get("HiddenSectionOffConfirm"), HumoyHiddenChats::clearAll);
        } else if (item.id == ACCOUNTS_OPEN) {
            HumoyAccountsActivity.openHidden(this);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
