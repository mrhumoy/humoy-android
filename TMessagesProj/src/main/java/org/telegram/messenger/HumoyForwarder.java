/*
 * This is the source code of Humoy for Android, based on Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Forwards messages from chats that restrict saving. The server refuses to forward them, so, as AyuGram
 * does, their files are downloaded and the messages are sent again as new ones, in the same order.
 */
public class HumoyForwarder {

    public static boolean isNeeded(int account, ArrayList<MessageObject> messages) {
        if (!HumoyConfig.saveProtectedContent) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(account);
        for (MessageObject message : messages) {
            if (message.messageOwner != null && message.messageOwner.humoyNoforwards || controller.isPeerNoForwardsReal(message.getDialogId())) {
                return true;
            }
        }
        return false;
    }

    public static void forward(int account, ArrayList<MessageObject> source, long peer, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        ArrayList<MessageObject> messages = new ArrayList<>(source);
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory factory = BulletinFactory.global();
            if (factory != null) {
                factory.createSimpleBulletin(R.raw.ic_download, HumoyStrings.get("ForwardingProtected")).show();
            }
            downloadThenSend(account, messages, peer, hideCaption, notify, scheduleDate, replyToTopMsg);
        });
    }

    private static boolean hasMedia(MessageObject message) {
        return message.getDocument() != null || MessageObject.getPhoto(message.messageOwner) != null;
    }

    /** The downloaded file of the message's photo or document, or null if it isn't on the device. */
    private static File localFile(int account, MessageObject message) {
        TLRPC.Message owner = message.messageOwner;
        if (!TextUtils.isEmpty(owner.attachPath)) {
            File file = new File(owner.attachPath);
            if (file.exists()) {
                return file;
            }
        }
        FileLoader loader = FileLoader.getInstance(account);
        File file = loader.getPathToMessage(owner);
        if (file != null && file.exists()) {
            return file;
        }
        TLRPC.Photo photo = MessageObject.getPhoto(owner);
        if (photo != null) {
            File best = null;
            int bestSize = 0;
            for (TLRPC.PhotoSize size : photo.sizes) {
                if (size instanceof TLRPC.TL_photoStrippedSize || size instanceof TLRPC.TL_photoPathSize) {
                    continue;
                }
                File f = loader.getPathToAttach(size, true);
                if (!f.exists()) {
                    f = loader.getPathToAttach(size, false);
                }
                if (f.exists() && size.w * size.h >= bestSize) {
                    best = f;
                    bestSize = size.w * size.h;
                }
            }
            return best;
        }
        return null;
    }

    private static void downloadThenSend(int account, ArrayList<MessageObject> messages, long peer, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        FileLoader loader = FileLoader.getInstance(account);
        HashSet<String> pending = new HashSet<>();
        for (MessageObject message : messages) {
            if (!hasMedia(message) || localFile(account, message) != null) {
                continue;
            }
            TLRPC.Document document = message.getDocument();
            if (document != null) {
                pending.add(FileLoader.getAttachFileName(document));
                loader.loadFile(document, message, FileLoader.PRIORITY_HIGH, 0);
            } else {
                TLRPC.Photo photo = MessageObject.getPhoto(message.messageOwner);
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (size != null) {
                    pending.add(FileLoader.getAttachFileName(size));
                    loader.loadFile(ImageLocation.getForPhoto(size, photo), message, "jpg", FileLoader.PRIORITY_HIGH, 0);
                }
            }
        }
        if (pending.isEmpty()) {
            send(account, messages, peer, hideCaption, notify, scheduleDate, replyToTopMsg);
            return;
        }
        NotificationCenter center = NotificationCenter.getInstance(account);
        NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int acc, Object... args) {
                // a failed download still lets the rest go; that message goes as text, if it has any
                if (args.length == 0 || !pending.remove((String) args[0]) || !pending.isEmpty()) {
                    return;
                }
                center.removeObserver(this, NotificationCenter.fileLoaded);
                center.removeObserver(this, NotificationCenter.fileLoadFailed);
                send(account, messages, peer, hideCaption, notify, scheduleDate, replyToTopMsg);
            }
        };
        center.addObserver(observer, NotificationCenter.fileLoaded);
        center.addObserver(observer, NotificationCenter.fileLoadFailed);
    }

    private static boolean isGalleryMedia(MessageObject message) {
        return message.isPhoto() || message.isVideo() && !message.isRoundVideo() && !message.isGif();
    }

    private static void send(int account, ArrayList<MessageObject> messages, long peer, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg) {
        AccountInstance accountInstance = AccountInstance.getInstance(account);
        SendMessagesHelper helper = SendMessagesHelper.getInstance(account);
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            String text = message.messageOwner.message == null ? "" : message.messageOwner.message;
            ArrayList<TLRPC.MessageEntity> entities = message.messageOwner.entities;
            if (hideCaption && hasMedia(message)) {
                text = "";
                entities = null;
            }
            File file = hasMedia(message) ? localFile(account, message) : null;

            if (file != null && isGalleryMedia(message)) {
                // photos and videos go like from the gallery; an album stays an album
                ArrayList<SendMessagesHelper.SendingMediaInfo> album = new ArrayList<>();
                long groupId = message.getGroupId();
                int j = i;
                while (j < messages.size()) {
                    MessageObject m = messages.get(j);
                    File f = j == i ? file : localFile(account, m);
                    if (j > i && (groupId == 0 || m.getGroupId() != groupId || f == null || !isGalleryMedia(m))) {
                        break;
                    }
                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                    info.path = f.getAbsolutePath();
                    info.isVideo = m.isVideo();
                    if (!hideCaption && !TextUtils.isEmpty(m.messageOwner.message)) {
                        info.caption = m.messageOwner.message;
                        info.entities = m.messageOwner.entities;
                    }
                    album.add(info);
                    j++;
                }
                i = j - 1;
                SendMessagesHelper.prepareSendingMedia(accountInstance, album, peer, null, replyToTopMsg, null, null, false, album.size() > 1,
                        null, notify, scheduleDate, 0, 0, false, null, null, 0, false, 0, 0, null);
            } else if (file != null && message.getDocument() != null) {
                TLRPC.TL_document document = copyDocument(account, message.getDocument(), file);
                helper.sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, file.getAbsolutePath(), peer, null, replyToTopMsg,
                        text, entities, null, null, notify, scheduleDate, 0, 0, null, null, false));
            } else if (!TextUtils.isEmpty(text)) {
                helper.sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer, null, replyToTopMsg, null, true,
                        entities, null, null, notify, scheduleDate, 0, null, false));
            }
        }
    }

    /** A new document for the downloaded file, so it is uploaded again instead of referring to the protected one. */
    private static TLRPC.TL_document copyDocument(int account, TLRPC.Document source, File file) {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.file_reference = new byte[0];
        document.dc_id = Integer.MIN_VALUE;
        document.mime_type = source.mime_type;
        document.file_name = source.file_name;
        document.file_name_fixed = source.file_name_fixed;
        document.date = ConnectionsManager.getInstance(account).getCurrentTime();
        document.size = file.length();
        document.attributes = source.attributes;
        document.localPath = file.getAbsolutePath();
        return document;
    }
}
