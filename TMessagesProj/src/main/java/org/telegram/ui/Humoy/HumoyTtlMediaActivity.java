/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.HumoyTtlMedia;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;

/** Self-destructing photos and videos Humoy saved on this device, as a grid; tap to view again. */
public class HumoyTtlMediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_DELETE_ALL = 1;

    private final ArrayList<HumoyTtlMedia.Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private GridLayoutManager layoutManager;
    private TextView emptyView;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.humoySelfDestructMediaSaved);
        reload();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.humoySelfDestructMediaSaved);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.humoySelfDestructMediaSaved) {
            reload();
        }
    }

    private void reload() {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<HumoyTtlMedia.Entry> loaded = HumoyTtlMedia.getInstance(currentAccount).getEntries();
            AndroidUtilities.runOnUIThread(() -> {
                entries.clear();
                entries.addAll(loaded);
                updateViews();
            });
        });
    }

    private void updateViews() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (actionBar != null) {
            actionBar.setSubtitle(entries.isEmpty() ? null : String.valueOf(entries.size()));
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(HumoyStrings.get("SavedSelfDestructMedia"));
        actionBar.createMenu().addItem(MENU_DELETE_ALL, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_DELETE_ALL && !entries.isEmpty()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(HumoyStrings.get("SelfDestructDeleteAll"));
                    builder.setMessage(HumoyStrings.get("SelfDestructDeleteAllConfirm"));
                    builder.setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> Utilities.globalQueue.postRunnable(() -> {
                        HumoyTtlMedia.getInstance(currentAccount).deleteAll();
                        AndroidUtilities.runOnUIThread(HumoyTtlMediaActivity.this::reload);
                    }));
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        layoutManager = new GridLayoutManager(context, 3);
        listView.setLayoutManager(layoutManager);
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
            }
        });
        listView.setAdapter(new Adapter());
        listView.setOnItemClickListener((view, position) -> openViewer(position));
        listView.setOnItemLongClickListener((view, position) -> {
            showOptions(position);
            return true;
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setText(HumoyStrings.get("SavedSelfDestructEmpty"));
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateViews();
        return fragmentView;
    }

    private String senderName(HumoyTtlMedia.Entry entry) {
        long id = entry.fromId != 0 ? entry.fromId : entry.dialogId;
        if (DialogObject.isEncryptedDialog(id)) {
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(id));
            id = encryptedChat != null ? encryptedChat.user_id : 0;
        }
        if (id > 0) {
            TLRPC.User user = getMessagesController().getUser(id);
            return user != null ? UserObject.getUserName(user) : "";
        } else if (id < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            return chat != null ? chat.title : "";
        }
        return "";
    }

    private void openViewer(int position) {
        if (position < 0 || position >= entries.size()) {
            return;
        }
        ArrayList<Object> photos = new ArrayList<>();
        for (int a = 0; a < entries.size(); a++) {
            HumoyTtlMedia.Entry entry = entries.get(a);
            MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, a, entry.date * 1000L, entry.path, 0, entry.isVideo(), 0, 0, entry.getFile().length());
            photos.add(photoEntry);
        }
        PhotoViewer.getInstance().setParentActivity(this);
        PhotoViewer.getInstance().openPhotoForSelect(photos, position, PhotoViewer.SELECT_TYPE_NO_SELECT, false, placeProvider, null);
    }

    private void showOptions(int position) {
        if (position < 0 || position >= entries.size() || getParentActivity() == null) {
            return;
        }
        HumoyTtlMedia.Entry entry = entries.get(position);
        CharSequence[] items = new CharSequence[]{
                HumoyStrings.get("SelfDestructOpenChat"),
                LocaleController.getString(R.string.SaveToGallery),
                HumoyStrings.get("SelfDestructDeleteCopy")
        };
        int[] icons = new int[]{R.drawable.msg_message, R.drawable.msg_gallery, R.drawable.msg_delete};
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(senderName(entry));
        builder.setItems(items, icons, (d, which) -> {
            if (which == 0) {
                Bundle args = new Bundle();
                if (DialogObject.isEncryptedDialog(entry.dialogId)) {
                    args.putInt("enc_id", DialogObject.getEncryptedChatId(entry.dialogId));
                } else if (entry.dialogId > 0) {
                    args.putLong("user_id", entry.dialogId);
                } else {
                    args.putLong("chat_id", -entry.dialogId);
                }
                args.putInt("message_id", entry.mid);
                presentFragment(new ChatActivity(args));
            } else if (which == 1) {
                MediaController.saveFile(entry.path, getParentActivity(), entry.isVideo() ? 1 : 0, null, null,
                        uri -> BulletinFactory.of(this).createDownloadBulletin(entry.isVideo() ? BulletinFactory.FileType.VIDEO : BulletinFactory.FileType.PHOTO).show());
            } else if (which == 2) {
                Utilities.globalQueue.postRunnable(() -> {
                    HumoyTtlMedia.getInstance(currentAccount).delete(entry);
                    AndroidUtilities.runOnUIThread(this::reload);
                });
            }
        });
        showDialog(builder.create());
    }

    private Cell cellAt(int index) {
        if (listView == null) {
            return null;
        }
        for (int a = 0, count = listView.getChildCount(); a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof Cell && ((Cell) child).position == index) {
                return (Cell) child;
            }
        }
        return null;
    }

    private final PhotoViewer.PhotoViewerProvider placeProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            Cell cell = cellAt(index);
            if (cell == null) {
                return null;
            }
            int[] coords = new int[2];
            cell.getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1];
            object.parentView = listView;
            object.imageReceiver = cell.imageReceiver;
            object.thumb = cell.imageReceiver.getBitmapSafe();
            return object;
        }

        @Override
        public void updatePhotoAtIndex(int index) {
            Cell cell = cellAt(index);
            if (cell != null) {
                cell.invalidate();
            }
        }
    };

    private class Cell extends FrameLayout {

        final ImageReceiver imageReceiver = new ImageReceiver(this);
        private final ImageView playView;
        private final TextView nameView;
        private final Paint shadowPaint = new Paint();
        int position;

        Cell(Context context) {
            super(context);
            setWillNotDraw(false);
            playView = new ImageView(context);
            playView.setImageResource(R.drawable.play_mini_video);
            playView.setColorFilter(0xffffffff);
            addView(playView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 6, 6, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            nameView.setTextColor(0xffffffff);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 6, 0, 6, 4));
        }

        void set(HumoyTtlMedia.Entry entry, int position) {
            this.position = position;
            int size = (int) (AndroidUtilities.displaySize.x / 3 / AndroidUtilities.density);
            String filter = size + "_" + size;
            if (entry.isVideo()) {
                imageReceiver.setImage(ImageLocation.getForPath("vthumb://0:" + entry.path), filter, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0);
            } else {
                imageReceiver.setImage(ImageLocation.getForPath("thumb://0:" + entry.path), filter, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0);
            }
            playView.setVisibility(entry.isVideo() ? VISIBLE : GONE);
            String name = senderName(entry);
            String date = LocaleController.formatDateChat(entry.date);
            nameView.setText(TextUtils.isEmpty(name) ? date : name + " · " + date);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            shadowPaint.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageReceiver.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            imageReceiver.setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight());
            imageReceiver.draw(canvas);
            canvas.drawRect(0, getMeasuredHeight() * 0.6f, getMeasuredWidth(), getMeasuredHeight(), shadowPaint);
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new Cell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((Cell) holder.itemView).set(entries.get(position), position);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }
}
