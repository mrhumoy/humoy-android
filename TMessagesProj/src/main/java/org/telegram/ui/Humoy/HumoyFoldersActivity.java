/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.view.View;

import org.telegram.messenger.HumoyFolders;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;

/** Which of Humoy's built-in folder tabs this account shows. */
public class HumoyFoldersActivity extends UniversalFragment {

    private static final int BUTTON_TELEGRAM_FOLDERS = 100;

    @Override
    protected CharSequence getTitle() {
        return HumoyStrings.get("FolderTabs");
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        HumoyFolders folders = HumoyFolders.getInstance(currentAccount);
        items.add(UItem.asHeader(HumoyStrings.get("FolderTabsHeader")));
        for (int i = 0; i < HumoyFolders.BUILT_IN.length; i++) {
            String key = HumoyFolders.BUILT_IN[i];
            items.add(UItem.asCheck(i, HumoyFolders.titleOf(key)).setChecked(!folders.isHidden(key)));
        }
        items.add(UItem.asShadow(HumoyStrings.get("FolderTabsInfo")));
        items.add(UItem.asButton(BUTTON_TELEGRAM_FOLDERS, R.drawable.msg_folders, HumoyStrings.get("FolderTabs") + " (Telegram)"));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_TELEGRAM_FOLDERS) {
            presentFragment(new FiltersSetupActivity());
            return;
        }
        if (item.id >= 0 && item.id < HumoyFolders.BUILT_IN.length) {
            String key = HumoyFolders.BUILT_IN[item.id];
            HumoyFolders folders = HumoyFolders.getInstance(currentAccount);
            boolean show = folders.isHidden(key);
            folders.setHidden(key, !show);
            ((TextCheckCell) view).setChecked(show);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
