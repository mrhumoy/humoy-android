/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.ui.Humoy;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.HumoyChat;
import org.telegram.messenger.HumoyServer;
import org.telegram.messenger.HumoyStrings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The verified "Humoy" chat, drawn by Telegram itself: each message from Humoy's admin panel becomes a
 * Telegram message shown in Telegram's message cell (text with links and @mentions, a photo, an inline
 * link button, the time), with Telegram's date labels over the chat wallpaper.
 */
public class HumoyChatActivity extends BaseFragment {

    // ids of the made-up photo files, far from Telegram's own
    private static final long PHOTO_VOLUME = 7_770_000_000L;

    private SizeNotifierFrameLayout root;
    private RecyclerListView listView;
    private final ArrayList<Object> rows = new ArrayList<>();
    private final ArrayList<String> downloading = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    confirmClear();
                }
            }
        });
        actionBar.createMenu().addItem(0, R.drawable.ic_ab_other).addSubItem(1, R.drawable.msg_delete, HumoyStrings.get("HumoyChatClear"));

        ChatAvatarContainer header = new ChatAvatarContainer(context, null, false) {
            @Override
            protected boolean onAvatarClick() {
                return true;
            }
        };
        header.setOccupyStatusBar(!AndroidUtilities.isTablet());
        header.setTitle("Humoy", false, false, true, false, null, false);
        header.setSubtitle(HumoyStrings.get("HumoyChatSubtitle"));
        header.getAvatarImageView().setImageResource(R.drawable.logo_middle);
        header.setClickable(false);
        actionBar.addView(header, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

        // this screen sits under the action bar, not behind it: draw the wallpaper from the top
        root = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
        };
        root.setOccupyStatusBar(false);
        root.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = root;

        listView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(10));
        listView.setAdapter(new Adapter());
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof ChatMessageCell) {
                showMessageOptions((ChatMessageCell) view);
                return true;
            }
            return false;
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        reload();
        HumoyChat.markRead(currentAccount);
        return fragmentView;
    }

    // ---- messages as Telegram messages

    private TLRPC.TL_photoSize photoSizeFor(int id) {
        TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
        size.type = "x";
        TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
        location.volume_id = PHOTO_VOLUME + id;
        location.local_id = 1;
        size.location = location;
        return size;
    }

    private File photoFile(int id) {
        return FileLoader.getInstance(currentAccount).getPathToAttach(photoSizeFor(id), true);
    }

    private MessageObject toMessage(JSONObject m) {
        int id = m.optInt("id");
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = id;
        message.date = m.optInt("date");
        message.dialog_id = 777000;
        message.flags = 257;
        message.from_id = new TLRPC.TL_peerUser();
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
        message.out = false;
        String title = m.optString("title");
        String text = m.optString("text");
        message.message = TextUtils.isEmpty(title) ? text : TextUtils.isEmpty(text) ? title : title + "\n" + text;
        addEntities(message, TextUtils.isEmpty(title) || TextUtils.isEmpty(text) ? 0 : title.length());

        String image = m.optString("image");
        File file = photoFile(id);
        if (HumoyServer.isImageUrl(image) && file.exists() && file.length() > 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            TLRPC.TL_photoSize size = photoSizeFor(id);
            size.w = Math.max(1, options.outWidth);
            size.h = Math.max(1, options.outHeight);
            // size 0: Telegram then looks for the file in its cache folder, where it was saved
            size.size = 0;
            TLRPC.TL_messageMediaPhoto media = new TLRPC.TL_messageMediaPhoto();
            media.flags |= 1;
            media.photo = new TLRPC.TL_photo();
            media.photo.id = PHOTO_VOLUME + id;
            media.photo.date = message.date;
            media.photo.file_reference = new byte[0];
            media.photo.sizes.add(size);
            message.media = media;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
        } else {
            message.media = new TLRPC.TL_messageMediaEmpty();
        }

        String button = m.optString("button");
        String link = m.optString("link").trim();
        if (!TextUtils.isEmpty(button) && !TextUtils.isEmpty(link)) {
            TL_keyboard.TL_inlineButtonTypeUrl type = new TL_keyboard.TL_inlineButtonTypeUrl();
            type.url = link.contains("://") ? link : "https://" + link;
            TL_keyboard.TL_keyboardInlineButton b = new TL_keyboard.TL_keyboardInlineButton();
            b.text = button;
            b.type = type;
            TL_keyboard.TL_keyboardInlineButtonRow row = new TL_keyboard.TL_keyboardInlineButtonRow();
            row.buttons.add(b);
            TLRPC.TL_replyInlineMarkup markup = new TLRPC.TL_replyInlineMarkup();
            markup.rows.add(row);
            message.reply_markup = markup;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MARKUP;
        }
        MessageObject object = new MessageObject(currentAccount, message, true, false);
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            object.mediaExists = true;
        }
        return object;
    }

    private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile(
            "(https?://[^\\s]+|(?:t\\.me|telegram\\.me|www\\.)[^\\s]*[^\\s.,!?)])|(?<![\\w@])@[A-Za-z][A-Za-z0-9_]{3,31}");

    /** Links and @mentions as Telegram entities, so the cell colors them and they open on tap; the old title stays bold. */
    private static void addEntities(TLRPC.TL_message message, int boldLength) {
        String text = message.message;
        if (boldLength > 0) {
            TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
            bold.offset = 0;
            bold.length = boldLength;
            message.entities.add(bold);
        }
        java.util.regex.Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            TLRPC.MessageEntity entity = matcher.group().startsWith("@") ? new TLRPC.TL_messageEntityMention() : new TLRPC.TL_messageEntityUrl();
            entity.offset = matcher.start();
            entity.length = matcher.end() - matcher.start();
            message.entities.add(entity);
        }
        if (!message.entities.isEmpty()) {
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
        }
    }

    private void reload() {
        rows.clear();
        JSONArray messages = HumoyChat.messages(currentAccount);
        String lastDay = null;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i);
            if (m == null) {
                continue;
            }
            String day = LocaleController.formatDateChat(m.optLong("date"));
            if (!day.equals(lastDay)) {
                lastDay = day;
                rows.add(m.optInt("date"));
            }
            rows.add(toMessage(m));
            downloadPhoto(m);
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
            listView.scrollToPosition(Math.max(0, rows.size() - 1));
        }
    }

    /** Fetches a message's picture once into Telegram's cache, then shows it as a photo. */
    private void downloadPhoto(JSONObject m) {
        String image = m.optString("image");
        int id = m.optInt("id");
        File file = photoFile(id);
        if (!HumoyServer.isImageUrl(image) || file.exists() || downloading.contains(image)) {
            return;
        }
        downloading.add(image);
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            HttpURLConnection connection = null;
            File part = new File(file.getPath() + ".part");
            try {
                connection = (HttpURLConnection) new URL(image).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                if (connection.getResponseCode() == 200) {
                    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                        byte[] buffer = new byte[32 * 1024];
                        int n;
                        long total = 0;
                        while ((n = in.read(buffer)) > 0 && total < 10 * 1024 * 1024) {
                            out.write(buffer, 0, n);
                            total += n;
                        }
                    }
                    ok = part.renameTo(file);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                part.delete();
            }
            boolean done = ok;
            AndroidUtilities.runOnUIThread(() -> {
                downloading.remove(image);
                if (done && fragmentView != null) {
                    reload();
                }
            });
        });
    }

    // ---- actions

    private void openLink(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        if (url.startsWith("@")) {
            url = "https://t.me/" + url.substring(1);
        } else if (!url.contains("://")) {
            url = "https://" + url;
        }
        Browser.openUrl(LaunchActivity.instance != null ? LaunchActivity.instance : getParentActivity(), url);
    }

    private void showMessageOptions(ChatMessageCell cell) {
        MessageObject object = cell.getMessageObject();
        if (object == null) {
            return;
        }
        int id = object.getId();
        String text = object.messageOwner.message;
        ItemOptions.makeOptions(this, cell)
                .add(R.drawable.msg_copy, LocaleController.getString(R.string.Copy), () -> {
                    AndroidUtilities.addToClipboard(text);
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                })
                .add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> {
                    HumoyChat.delete(currentAccount, Collections.singletonList(id));
                    reload();
                })
                .show();
    }

    private void confirmClear() {
        if (getParentActivity() == null || HumoyChat.messages(currentAccount).length() == 0) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(HumoyStrings.get("HumoyChatClear"))
                .setMessage(HumoyStrings.get("HumoyChatClearConfirm"))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                    HumoyChat.deleteAll(currentAccount);
                    reload();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ((TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // ---- list

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.itemView instanceof ChatMessageCell;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof MessageObject ? 0 : 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                ChatMessageCell cell = new ChatMessageCell(parent.getContext(), currentAccount);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean canPerformActions() {
                        return true;
                    }

                    @Override
                    public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                        if (url instanceof URLSpanNoUnderline) {
                            openLink(((URLSpanNoUnderline) url).getURL());
                        } else if (url instanceof URLSpan) {
                            openLink(((URLSpan) url).getURL());
                        }
                    }

                    @Override
                    public void didPressBotButton(ChatMessageCell cell, TL_keyboard.KeyboardButtonProto button) {
                        if (button instanceof TL_keyboard.KeyboardInlineButton
                                && ((TL_keyboard.KeyboardInlineButton) button).type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
                            openLink(((TL_keyboard.TL_inlineButtonTypeUrl) ((TL_keyboard.KeyboardInlineButton) button).type).url);
                        }
                    }

                });
                view = cell;
            } else {
                view = new ChatActionCell(parent.getContext());
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder.itemView instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                cell.isChat = false;
                cell.setFullyDraw(true);
                cell.setMessageObject((MessageObject) row, null, false, false, false);
            } else if (holder.itemView instanceof ChatActionCell) {
                ((ChatActionCell) holder.itemView).setCustomDate((Integer) row, false, false);
            }
        }
    }
}
