/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.HumoyMessageStore;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Messages Humoy kept on this device, drawn as chat bubbles over the chat's wallpaper:
 * either the earlier versions of one edited message, or every deleted message of a chat.
 */
public class HumoyMessagesActivity extends BaseFragment {

    private static final int MODE_EDIT_HISTORY = 0;
    private static final int MODE_DELETED = 1;

    private static final int VIEW_MESSAGE = 0;
    private static final int VIEW_DATE = 1;

    private final int mode;
    private final long dialogId;
    private final MessageObject editedMessage;
    // the chat's own colors and wallpaper, so this screen looks like the chat it came from
    private final Theme.ResourcesProvider chatTheme;
    private final Drawable wallpaper;
    private final boolean wallpaperMotion;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    // what the list shows: a MessageObject, or an Integer date for a day header
    private final ArrayList<Object> rows = new ArrayList<>();
    private String searchQuery = "";

    private RecyclerListView listView;
    private ChatAvatarContainer avatarContainer;

    /** Earlier versions of an edited message; the current one stays in the chat. */
    public static HumoyMessagesActivity editHistory(MessageObject message, Theme.ResourcesProvider chatTheme, Drawable wallpaper, boolean wallpaperMotion) {
        return new HumoyMessagesActivity(MODE_EDIT_HISTORY, message.getDialogId(), message, chatTheme, wallpaper, wallpaperMotion);
    }

    /** Every message of the chat that was deleted while Humoy kept it. */
    public static HumoyMessagesActivity deletedMessages(long dialogId, Theme.ResourcesProvider chatTheme, Drawable wallpaper, boolean wallpaperMotion) {
        return new HumoyMessagesActivity(MODE_DELETED, dialogId, null, chatTheme, wallpaper, wallpaperMotion);
    }

    private HumoyMessagesActivity(int mode, long dialogId, MessageObject editedMessage, Theme.ResourcesProvider chatTheme, Drawable wallpaper, boolean wallpaperMotion) {
        this.mode = mode;
        this.dialogId = dialogId;
        this.editedMessage = editedMessage;
        this.chatTheme = chatTheme;
        this.wallpaper = wallpaper;
        this.wallpaperMotion = wallpaperMotion;
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return chatTheme;
    }

    @Override
    public boolean onFragmentCreate() {
        HumoyMessageStore store = HumoyMessageStore.getInstance(currentAccount);
        if (mode == MODE_EDIT_HISTORY) {
            for (HumoyMessageStore.EditVersion version : store.getEditHistory(dialogId, editedMessage.getId())) {
                TLRPC.Message message = version.message;
                if (message == null) {
                    // saved before whole messages were kept: rebuild it from the current one with the old text
                    message = copyOf(editedMessage.messageOwner);
                    if (message == null) {
                        continue;
                    }
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    message.message = version.text;
                    message.entities = new ArrayList<>();
                }
                store.restoreMedia(message);
                message.date = version.date;
                message.edit_date = 0;
                message.flags &= ~TLRPC.MESSAGE_FLAG_EDITED;
                message.reactions = null;
                message.replies = null;
                message.dialog_id = dialogId;
                messages.add(new MessageObject(currentAccount, message, true, true));
            }
            updateRows();
        } else {
            store.loadDeletedMessages(dialogId, loaded -> {
                messages.clear();
                messages.addAll(loaded);
                updateRows();
                updateSubtitle();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            });
        }
        return super.onFragmentCreate();
    }

    private static TLRPC.Message copyOf(TLRPC.Message message) {
        try {
            org.telegram.tgnet.SerializedData data = new org.telegram.tgnet.SerializedData(message.getObjectSize());
            message.serializeToStream(data);
            org.telegram.tgnet.SerializedData in = new org.telegram.tgnet.SerializedData(data.toByteArray());
            TLRPC.Message copy = TLRPC.Message.TLdeserialize(in, in.readInt32(false), false);
            data.cleanup();
            in.cleanup();
            if (copy != null) {
                copy.attachPath = message.attachPath;
            }
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateRows() {
        rows.clear();
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        int lastDay = -1;
        for (MessageObject message : messages) {
            if (!query.isEmpty()) {
                String text = message.messageOwner.message;
                if (text == null || !text.toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
            }
            if (mode == MODE_DELETED) {
                int day = dayOf(message.messageOwner.date);
                if (day != lastDay) {
                    rows.add(message.messageOwner.date);
                    lastDay = day;
                }
            }
            rows.add(message);
        }
    }

    private static int dayOf(int date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis((long) date * 1000);
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private void updateSubtitle() {
        if (avatarContainer == null) {
            return;
        }
        if (mode == MODE_DELETED) {
            avatarContainer.setSubtitle(LocaleController.formatPluralString("messages", messages.size()));
        } else if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            avatarContainer.setSubtitle(user != null ? LocaleController.formatUserStatus(currentAccount, user) : "");
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            avatarContainer.setSubtitle(chat != null && chat.participants_count > 0
                    ? LocaleController.formatPluralString(ChatObject.isChannelAndNotMegaGroup(chat) ? "Subscribers" : "Members", chat.participants_count)
                    : HumoyStrings.get("EditHistory"));
        }
    }

    /** Bubbles of one sender close in time stick together, as in a chat. */
    private boolean sameGroup(Object a, Object b) {
        if (mode != MODE_DELETED || !(a instanceof MessageObject) || !(b instanceof MessageObject)) {
            return false;
        }
        MessageObject m1 = (MessageObject) a, m2 = (MessageObject) b;
        return m1.isOutOwner() == m2.isOutOwner() && m1.getFromChatId() == m2.getFromChatId()
                && Math.abs(m1.messageOwner.date - m2.messageOwner.date) <= 5 * 60;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(ColorUtils.setAlphaComponent(BlurredBackgroundProviderImpl.topPanelChatActivity(chatTheme).getBackgroundColor(), 255));
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);

        avatarContainer = new ChatAvatarContainer(context, null, false, chatTheme);
        avatarContainer.setEnabled(false);
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) {
                avatarContainer.setUserAvatar(user, true);
                avatarContainer.setTitle(UserObject.getUserName(user));
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null) {
                avatarContainer.setChatAvatar(chat);
                avatarContainer.setTitle(chat.title);
            }
        }
        updateSubtitle();
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        if (mode == MODE_DELETED) {
            ActionBarMenuItem searchItem = actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override
                        public void onSearchExpand() {
                            avatarContainer.setVisibility(View.GONE);
                        }

                        @Override
                        public void onSearchCollapse() {
                            avatarContainer.setVisibility(View.VISIBLE);
                            setSearchQuery("");
                        }

                        @Override
                        public void onTextChanged(EditText editText) {
                            setSearchQuery(editText.getText().toString());
                        }
                    });
            searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        }

        // this view starts below the action bar, so the wallpaper must not leave room for one
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
        };
        contentView.setOccupyStatusBar(false);
        contentView.setBackgroundImage(wallpaper != null ? wallpaper : Theme.getCachedWallpaper(), wallpaperMotion);
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return false;
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }

            @Override
            public int getItemViewType(int position) {
                return rows.get(position) instanceof MessageObject ? VIEW_MESSAGE : VIEW_DATE;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_DATE) {
                    view = new ChatActionCell(context, false, chatTheme);
                } else {
                    view = createMessageCell(context);
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                Object row = rows.get(position);
                if (holder.itemView instanceof ChatActionCell) {
                    ((ChatActionCell) holder.itemView).setCustomDate((Integer) row, false, false);
                    return;
                }
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                boolean pinnedTop = position > 0 && sameGroup(rows.get(position - 1), row);
                boolean pinnedBottom = position < rows.size() - 1 && sameGroup(row, rows.get(position + 1));
                // as in a group chat, so the bubble leaves room for the avatar
                cell.isChat = true;
                cell.setMessageObject((MessageObject) row, null, pinnedBottom, pinnedTop, false);
                cell.getPhotoImage().setCrossfadeDuration(0);
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void setSearchQuery(String query) {
        if (TextUtils.equals(searchQuery, query)) {
            return;
        }
        searchQuery = query;
        updateRows();
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private ChatMessageCell createMessageCell(Context context) {
        ChatMessageCell cell = new ChatMessageCell(context, currentAccount, false, null, chatTheme) {
            @Override
            protected boolean isNeedAuthorName() {
                // the chat header already says who it is; groups still show names
                return dialogId < 0 && super.isNeedAuthorName();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // in a chat the list draws avatars; here a bubble draws its own, at the bottom of its group
                ImageReceiver avatar = getAvatarImage();
                if (needDrawAvatar() && !isPinnedBottom() && avatar != null && avatar.getImageHeight() != 0) {
                    avatar.setImageY(getMeasuredHeight() - avatar.getImageHeight() - AndroidUtilities.dp(4));
                    avatar.draw(canvas);
                }
            }

            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                super.didSetImage(imageReceiver, set, thumb, memCache);
                // without this the image could stay blank (white) until the cell was touched
                postInvalidate();
            }
        };
        cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override
            public boolean canPerformActions() {
                return true;
            }

            @Override
            public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                if (user == null) {
                    return;
                }
                if (user.id == dialogId) {
                    // we came from this very chat: just go back to it
                    finishFragment();
                } else {
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
                    presentFragment(new ChatActivity(args));
                }
            }

            @Override
            public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                MessageObject message = cell.getMessageObject();
                if (message == null || getParentActivity() == null) {
                    return;
                }
                if (message.isPhoto() || message.isVideo() || message.isGif()) {
                    PhotoViewer.getInstance().setParentActivity(HumoyMessagesActivity.this);
                    PhotoViewer.getInstance().openPhoto(message, null, 0, 0, 0, photoViewerProvider);
                }
            }
        });
        return cell;
    }

    private void redrawCells() {
        if (listView == null) {
            return;
        }
        for (int a = 0, count = listView.getChildCount(); a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).getPhotoImage().setVisible(true, true);
                child.invalidate();
            }
        }
        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    // opens photos from their bubble and puts them back there on close, like in a chat
    private final PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (listView == null || messageObject == null) {
                return null;
            }
            for (int a = 0, count = listView.getChildCount(); a < count; a++) {
                View view = listView.getChildAt(a);
                if (!(view instanceof ChatMessageCell)) {
                    continue;
                }
                ChatMessageCell cell = (ChatMessageCell) view;
                MessageObject message = cell.getMessageObject();
                // versions of one message share its id, so look for this very one
                if (message == null || message != messageObject && message.messageOwner != messageObject.messageOwner) {
                    continue;
                }
                ImageReceiver imageReceiver = cell.getPhotoImage();
                int[] coords = new int[2];
                view.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1];
                object.parentView = listView;
                object.imageReceiver = imageReceiver;
                object.thumb = imageReceiver.getBitmapSafe();
                object.radius = imageReceiver.getRoundRadius(true);
                return object;
            }
            return null;
        }

        @Override
        public void willHidePhotoViewer() {
            redrawCells();
        }

        @Override
        public void onClose() {
            AndroidUtilities.runOnUIThread(HumoyMessagesActivity.this::redrawCells);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // images are released while another screen is open; bind the cells again so they load back
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }
}
