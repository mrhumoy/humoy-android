/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.HumoyConfig;
import org.telegram.messenger.HumoyContactChanges;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.UsersSelectActivity;

import java.util.ArrayList;

/** Settings of contact changes: order, first view, which kinds are kept and whose. */
public class HumoyContactChangesSettingsActivity extends UniversalFragment {

    private static final int CHECK_UNSEEN_FIRST = 1;
    private static final int BUTTON_VIEW = 2;
    private static final int BUTTON_SCOPE = 3;
    private static final int BUTTON_CUSTOM = 4;
    private static final int CHECK_TYPE = 100; // + type

    private static final String[] TYPE_KEYS = {"CcPhoto", "CcPhotoRemoved", "CcPhone", "CcName", "CcUsername", "CcBlocked", "CcUnblocked"};
    private static final String[] SCOPE_KEYS = {"CcScopeAll", "CcScopeContacts", "CcScopeMutual", "CcScopeCustom", "CcScopeNone"};

    @Override
    protected CharSequence getTitle() {
        return HumoyStrings.get("CcSettings");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(HumoyStrings.get("CcSettingsHeader")));
        items.add(UItem.asCheck(CHECK_UNSEEN_FIRST, HumoyStrings.get("CcUnseenFirst")).setChecked(HumoyConfig.contactChangesUnseenFirst));
        items.add(UItem.asButton(BUTTON_VIEW, HumoyStrings.get("CcDefaultView"),
                HumoyStrings.get(HumoyConfig.contactChangesView == 1 ? "CcViewAll" : "CcViewPeople")));
        items.add(UItem.asShadow(HumoyStrings.get("CcSettingsInfo")));

        items.add(UItem.asHeader(HumoyStrings.get("CcTypesHeader")));
        for (int t = 0; t < HumoyContactChanges.TYPES_COUNT; t++) {
            items.add(UItem.asCheck(CHECK_TYPE + t, HumoyStrings.get(TYPE_KEYS[t])).setChecked((HumoyConfig.contactChangesTypes & (1 << t)) != 0));
        }
        items.add(UItem.asShadow(HumoyStrings.get("CcTypesInfo")));

        items.add(UItem.asHeader(HumoyStrings.get("CcScopeHeader")));
        items.add(UItem.asButton(BUTTON_SCOPE, HumoyStrings.get("CcScope"), HumoyStrings.get(SCOPE_KEYS[HumoyConfig.contactChangesScope])));
        if (HumoyConfig.contactChangesScope == HumoyContactChanges.SCOPE_CUSTOM) {
            items.add(UItem.asButton(BUTTON_CUSTOM, HumoyStrings.get("CcCustomPeople"), String.valueOf(HumoyConfig.getWatchedContacts().size())));
        }
        items.add(UItem.asShadow(HumoyStrings.get("CcScopeInfo")));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == CHECK_UNSEEN_FIRST) {
            ((TextCheckCell) view).setChecked(HumoyConfig.contactChangesUnseenFirst = !HumoyConfig.contactChangesUnseenFirst);
            HumoyConfig.saveConfig();
        } else if (item.id == BUTTON_VIEW) {
            choose(HumoyStrings.get("CcDefaultView"), new String[]{HumoyStrings.get("CcViewPeople"), HumoyStrings.get("CcViewAll")},
                    HumoyConfig.contactChangesView, which -> {
                        HumoyConfig.contactChangesView = which;
                        HumoyConfig.saveConfig();
                        listView.adapter.update(true);
                    });
        } else if (item.id == BUTTON_SCOPE) {
            String[] names = new String[SCOPE_KEYS.length];
            for (int i = 0; i < names.length; i++) {
                names[i] = HumoyStrings.get(SCOPE_KEYS[i]);
            }
            choose(HumoyStrings.get("CcScope"), names, HumoyConfig.contactChangesScope, which -> {
                HumoyConfig.contactChangesScope = which;
                HumoyConfig.saveConfig();
                listView.adapter.update(true);
                if (which == HumoyContactChanges.SCOPE_CUSTOM && HumoyConfig.getWatchedContacts().isEmpty()) {
                    pickPeople();
                }
            });
        } else if (item.id == BUTTON_CUSTOM) {
            pickPeople();
        } else if (item.id >= CHECK_TYPE && item.id < CHECK_TYPE + HumoyContactChanges.TYPES_COUNT) {
            int bit = 1 << (item.id - CHECK_TYPE);
            HumoyConfig.contactChangesTypes ^= bit;
            HumoyConfig.saveConfig();
            ((TextCheckCell) view).setChecked((HumoyConfig.contactChangesTypes & bit) != 0);
        }
    }

    private void pickPeople() {
        UsersSelectActivity activity = new UsersSelectActivity(true, new ArrayList<>(HumoyConfig.getWatchedContacts()), 0).asPrivateChats();
        activity.setDelegate((ids, flags) -> {
            ArrayList<Long> users = new ArrayList<>();
            for (Long id : ids) {
                if (id != null && id > 0) {
                    users.add(id);
                }
            }
            HumoyConfig.setWatchedContacts(users);
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        presentFragment(activity);
    }

    private void choose(String title, String[] options, int selected, org.telegram.messenger.Utilities.Callback<Integer> onChosen) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        AlertDialog[] dialog = new AlertDialog[1];
        for (int i = 0; i < options.length; i++) {
            RadioColorCell cell = new RadioColorCell(context);
            cell.setPadding(org.telegram.messenger.AndroidUtilities.dp(4), 0, org.telegram.messenger.AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(options[i], i == selected);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            int which = i;
            cell.setOnClickListener(v -> {
                if (dialog[0] != null) {
                    dialog[0].dismiss();
                }
                onChosen.run(which);
            });
            layout.addView(cell);
        }
        builder.setView(layout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialog[0] = builder.create();
        showDialog(dialog[0]);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
