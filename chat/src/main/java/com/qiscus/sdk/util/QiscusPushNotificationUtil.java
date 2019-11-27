/*
 * Copyright (c) 2016 Qiscus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qiscus.sdk.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import android.text.TextUtils;

import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.qiscus.nirmana.Nirmana;
import com.qiscus.sdk.Qiscus;
import com.qiscus.sdk.R;
import com.qiscus.sdk.chat.core.data.local.QiscusCacheManager;
import com.qiscus.sdk.chat.core.data.model.QParticipant;
import com.qiscus.sdk.chat.core.data.model.QChatRoom;
import com.qiscus.sdk.chat.core.data.model.QMessage;
import com.qiscus.sdk.chat.core.data.model.QiscusPushNotificationMessage;
import com.qiscus.sdk.chat.core.data.remote.QiscusApi;
import com.qiscus.sdk.chat.core.util.BuildVersionUtil;
import com.qiscus.sdk.chat.core.util.QiscusAndroidUtil;
import com.qiscus.sdk.chat.core.util.QiscusNumberUtil;
import com.qiscus.sdk.chat.core.util.QiscusRawDataExtractor;
import com.qiscus.sdk.chat.core.util.QiscusTextUtil;
import com.qiscus.sdk.data.model.QiscusMentionConfig;
import com.qiscus.sdk.service.QiscusPushNotificationClickReceiver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static com.qiscus.sdk.chat.core.util.BuildVersionUtil.isNougatOrHigher;

/**
 * Created on : June 15, 2017
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public final class QiscusPushNotificationUtil {
    public static final String KEY_NOTIFICATION_REPLY = "KEY_NOTIFICATION_REPLY";

    public static void handlePushNotification(Context context, QMessage qiscusMessage) {
        QiscusAndroidUtil.runOnBackgroundThread(() -> handlePN(context, qiscusMessage));
    }

    public static void handleDeletedCommentNotification(Context context, List<QMessage> comments, boolean hardDelete) {
        QiscusAndroidUtil.runOnBackgroundThread(() -> handleDeletedComment(context, comments, hardDelete));
    }

    private static void handlePN(Context context, QMessage qiscusMessage) {
        if (Qiscus.getDataStore().isContains(qiscusMessage)) {
            return;
        }

        Qiscus.getDataStore().addOrUpdate(qiscusMessage);

        Pair<Boolean, Long> lastChatActivity = QiscusCacheManager.getInstance().getLastChatActivity();
        if (!lastChatActivity.first || lastChatActivity.second != qiscusMessage.getChatRoomId()) {
            updateUnreadCount(qiscusMessage);
        }

        if (Qiscus.getChatConfig().isEnablePushNotification()
                && !qiscusMessage.getSenderEmail().equalsIgnoreCase(Qiscus.getQiscusAccount().getId())) {
            if (Qiscus.getChatConfig().isOnlyEnablePushNotificationOutsideChatRoom()) {
                if (!lastChatActivity.first || lastChatActivity.second != qiscusMessage.getChatRoomId()) {
                    showPushNotification(context, qiscusMessage);
                }
            } else {
                showPushNotification(context, qiscusMessage);
            }
        }
    }

    private static void updateUnreadCount(QMessage qiscusMessage) {
        QChatRoom room = Qiscus.getDataStore().getChatRoom(qiscusMessage.getChatRoomId());
        if (room == null) {
            fetchRoomData(qiscusMessage.getChatRoomId());
            return;
        }

        if (qiscusMessage.isMyComment()) {
            room.setUnreadCount(0);
        } else {
            room.setUnreadCount(room.getUnreadCount() + 1);
        }
        Qiscus.getDataStore().addOrUpdate(room);
    }

    private static void fetchRoomData(long roomId) {
        QiscusApi.getInstance()
                .getChatRoom(roomId)
                .doOnNext(qiscusChatRoom -> Qiscus.getDataStore().addOrUpdate(qiscusChatRoom))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(qiscusChatRoom -> {
                }, throwable -> {
                });
    }

    private static void showPushNotification(Context context, QMessage comment) {
        String messageText = comment.isGroupMessage() ? comment.getSender().getName().split(" ")[0] + ": " : "";
        if (comment.getType() == QMessage.Type.SYSTEM_EVENT) {
            messageText = "";
        }

        QiscusMentionConfig mentionConfig = Qiscus.getChatConfig().getMentionConfig();
        Map<String, QParticipant> members = new HashMap<>();
        if (mentionConfig.isEnableMention()) {
            QChatRoom room = Qiscus.getDataStore().getChatRoom(comment.getChatRoomId());
            if (room != null) {
                for (QParticipant member : room.getParticipants()) {
                    members.put(member.getId(), member);
                }
            }
        }

        switch (comment.getType()) {
            case IMAGE:
                if (mentionConfig.isEnableMention()) {
                    messageText += "\uD83D\uDCF7 " + (TextUtils.isEmpty(comment.getCaption()) ?
                            QiscusTextUtil.getString(R.string.qiscus_send_a_photo) :
                            new QiscusSpannableBuilder(comment.getCaption(), members).build().toString());
                } else {
                    messageText += "\uD83D\uDCF7 " + (TextUtils.isEmpty(comment.getCaption()) ?
                            QiscusTextUtil.getString(R.string.qiscus_send_a_photo) : comment.getCaption());
                }
                break;
            case VIDEO:
                if (mentionConfig.isEnableMention()) {
                    messageText += "\uD83C\uDFA5 " + (TextUtils.isEmpty(comment.getCaption()) ?
                            QiscusTextUtil.getString(R.string.qiscus_send_a_video) :
                            new QiscusSpannableBuilder(comment.getCaption(), members).build().toString());
                } else {
                    messageText += "\uD83C\uDFA5 " + (TextUtils.isEmpty(comment.getCaption()) ?
                            QiscusTextUtil.getString(R.string.qiscus_send_a_video) : comment.getCaption());
                }
                break;
            case AUDIO:
                messageText += "\uD83D\uDD0A " + QiscusTextUtil.getString(R.string.qiscus_send_a_audio);
                break;
            case CONTACT:
                messageText += "\u260E " + QiscusTextUtil.getString(R.string.qiscus_contact) + ": " +
                        comment.getContact().getName();
                break;
            case LOCATION:
                messageText += "\uD83D\uDCCD " + comment.getMessage();
                break;
            case CAROUSEL:
                try {
                    JSONObject payload = QiscusRawDataExtractor.getPayload(comment);
                    JSONArray cards = payload.optJSONArray("cards");
                    if (cards.length() > 0) {
                        messageText += "\uD83D\uDCDA " + cards.optJSONObject(0).optString("title");
                    } else {
                        messageText += "\uD83D\uDCDA " + QiscusTextUtil.getString(R.string.qiscus_send_a_carousel);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    messageText += "\uD83D\uDCDA " + QiscusTextUtil.getString(R.string.qiscus_send_a_carousel);
                }
                break;
            default:
                if (mentionConfig.isEnableMention()) {
                    messageText += comment.isAttachment() ? "\uD83D\uDCC4 " +
                            QiscusTextUtil.getString(R.string.qiscus_send_attachment) :
                            new QiscusSpannableBuilder(comment.getMessage(), members).build().toString();
                } else {
                    messageText += comment.isAttachment() ? "\uD83D\uDCC4 " +
                            QiscusTextUtil.getString(R.string.qiscus_send_attachment) : comment.getMessage();
                }
                break;
        }

        QiscusPushNotificationMessage pushNotificationMessage =
                new QiscusPushNotificationMessage(comment.getId(), messageText);
        pushNotificationMessage.setRoomName(comment.getRoomName());
        pushNotificationMessage.setRoomAvatar(comment.getRoomAvatar());
        if (!QiscusCacheManager.getInstance()
                .addMessageNotifItem(pushNotificationMessage, comment.getChatRoomId())) {
            return;
        }

        if (Qiscus.getChatConfig().isEnableAvatarAsNotificationIcon()) {
            QiscusAndroidUtil.runOnUIThread(() -> loadAvatar(context, comment, pushNotificationMessage));
        } else {
            pushNotification(context, comment, pushNotificationMessage,
                    BitmapFactory.decodeResource(context.getResources(), Qiscus.getChatConfig().getNotificationBigIcon()));
        }
    }

    private static void loadAvatar(Context context, QMessage comment, QiscusPushNotificationMessage pushNotificationMessage) {
        Nirmana.getInstance().get()
                .asBitmap()
                .load(pushNotificationMessage.getRoomAvatar())
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        QiscusAndroidUtil.runOnBackgroundThread(() -> {
                            try {
                                pushNotification(context, comment, pushNotificationMessage, QiscusImageUtil.getCircularBitmap(resource));
                            } catch (Exception e) {
                                pushNotification(context, comment, pushNotificationMessage,
                                        BitmapFactory.decodeResource(context.getResources(),
                                                Qiscus.getChatConfig().getNotificationBigIcon()));
                            }
                        });
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        QiscusAndroidUtil.runOnBackgroundThread(() -> pushNotification(context, comment, pushNotificationMessage,
                                BitmapFactory.decodeResource(context.getResources(),
                                        Qiscus.getChatConfig().getNotificationBigIcon())));
                    }
                });
    }

    private static void pushNotification(Context context, QMessage comment,
                                         QiscusPushNotificationMessage pushNotificationMessage, Bitmap largeIcon) {

        String notificationChannelId = Qiscus.getApps().getPackageName() + ".qiscus.sdk.notification.channel";
        if (BuildVersionUtil.isOreoOrHigher()) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(notificationChannelId, "Chat", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }

        PendingIntent pendingIntent;
        Intent openIntent = new Intent(context, QiscusPushNotificationClickReceiver.class);
        openIntent.putExtra("data", comment);
        pendingIntent = PendingIntent.getBroadcast(context, QiscusNumberUtil.convertToInt(comment.getChatRoomId()),
                openIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, notificationChannelId);
        notificationBuilder.setContentTitle(pushNotificationMessage.getRoomName())
                .setContentIntent(pendingIntent)
                .setContentText(pushNotificationMessage.getMessage())
                .setTicker(pushNotificationMessage.getMessage())
                .setSmallIcon(Qiscus.getChatConfig().getNotificationSmallIcon())
                .setLargeIcon(largeIcon)
                .setColor(ContextCompat.getColor(context, Qiscus.getChatConfig().getInlineReplyColor()))
                .setGroup("CHAT_NOTIF_" + comment.getChatRoomId())
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        if (Qiscus.getChatConfig().isEnableReplyNotification() && isNougatOrHigher()) {
            String getRepliedTo = pushNotificationMessage.getRoomName();
            RemoteInput remoteInput = new RemoteInput.Builder(KEY_NOTIFICATION_REPLY)
                    .setLabel(QiscusTextUtil.getString(R.string.qiscus_reply_to, getRepliedTo.toUpperCase()))
                    .build();

            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send,
                    QiscusTextUtil.getString(R.string.qiscus_reply_to, getRepliedTo.toUpperCase()), pendingIntent)
                    .addRemoteInput(remoteInput)
                    .build();
            notificationBuilder.addAction(replyAction);
        }

        boolean cancel = false;
        if (Qiscus.getChatConfig().getNotificationBuilderInterceptor() != null) {
            cancel = !Qiscus.getChatConfig().getNotificationBuilderInterceptor()
                    .intercept(notificationBuilder, comment);
        }

        if (cancel) {
            return;
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        List<QiscusPushNotificationMessage> notifItems = QiscusCacheManager.getInstance()
                .getMessageNotifItems(comment.getChatRoomId());
        if (notifItems == null) {
            notifItems = new ArrayList<>();
        }
        int notifSize = 5;
        if (notifItems.size() < notifSize) {
            notifSize = notifItems.size();
        }
        if (notifItems.size() > notifSize) {
            inboxStyle.addLine(".......");
        }
        int start = notifItems.size() - notifSize;
        for (int i = start; i < notifItems.size(); i++) {
            inboxStyle.addLine(notifItems.get(i).getMessage());
        }
        inboxStyle.setSummaryText(QiscusTextUtil.getString(R.string.qiscus_notif_count, notifItems.size()));
        notificationBuilder.setStyle(inboxStyle);

        if (notifSize <= 3) {
            notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
        }

        QiscusAndroidUtil.runOnUIThread(() -> NotificationManagerCompat.from(context)
                .notify(QiscusNumberUtil.convertToInt(comment.getChatRoomId()), notificationBuilder.build()));
    }

    private static void handleDeletedComment(Context context, List<QMessage> comments, boolean hardDelete) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        QMessage qiscusMessage = comments.get(comments.size() - 1);

        if (hardDelete) {
            boolean removeItem = false;
            for (QMessage comment : comments) {
                if (QiscusCacheManager.getInstance()
                        .removeMessageNotifItem(new QiscusPushNotificationMessage(comment), comment.getChatRoomId())) {
                    removeItem = true;
                }
            }

            if (removeItem) {
                updateNotification(context, qiscusMessage);
            }
        } else {
            boolean updateItem = false;
            for (QMessage comment : comments) {
                if (QiscusCacheManager.getInstance()
                        .updateMessageNotifItem(new QiscusPushNotificationMessage(comment), comment.getChatRoomId())) {
                    updateItem = true;
                }
            }

            if (updateItem) {
                updateNotification(context, qiscusMessage);
            }
        }
    }

    private static void updateNotification(Context context, QMessage qiscusMessage) {
        if (Qiscus.getChatConfig().isEnablePushNotification()
                && !qiscusMessage.getSenderEmail().equalsIgnoreCase(Qiscus.getQiscusAccount().getId())) {
            if (Qiscus.getChatConfig().isOnlyEnablePushNotificationOutsideChatRoom()) {
                Pair<Boolean, Long> lastChatActivity = QiscusCacheManager.getInstance().getLastChatActivity();
                if (!lastChatActivity.first || lastChatActivity.second != qiscusMessage.getChatRoomId()) {
                    updatePushNotification(context, qiscusMessage);
                }
            } else {
                updatePushNotification(context, qiscusMessage);
            }
        }
    }

    private static void updatePushNotification(Context context, QMessage qiscusMessage) {
        List<QiscusPushNotificationMessage> items = QiscusCacheManager.getInstance()
                .getMessageNotifItems(qiscusMessage.getChatRoomId());

        if (items == null || items.isEmpty()) {
            QiscusAndroidUtil.runOnUIThread(() -> clearPushNotification(context, qiscusMessage.getChatRoomId()));
            return;
        }

        QiscusPushNotificationMessage lastMessage = items.get(items.size() - 1);
        if (Qiscus.getChatConfig().isEnableAvatarAsNotificationIcon()) {
            QiscusAndroidUtil.runOnUIThread(() -> loadAvatar(context, qiscusMessage, lastMessage));
        } else {
            pushNotification(context, qiscusMessage, lastMessage,
                    BitmapFactory.decodeResource(context.getResources(), Qiscus.getChatConfig().getNotificationBigIcon()));
        }
    }

    public static void clearPushNotification(Context context, long roomId) {
        NotificationManagerCompat.from(context).cancel(QiscusNumberUtil.convertToInt(roomId));
        QiscusCacheManager.getInstance().clearMessageNotifItems(roomId);
    }
}
