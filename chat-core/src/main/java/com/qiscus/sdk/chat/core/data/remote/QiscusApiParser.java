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

package com.qiscus.sdk.chat.core.data.remote;

import androidx.core.util.Pair;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qiscus.sdk.chat.core.data.model.QAccount;
import com.qiscus.sdk.chat.core.data.model.QChatRoom;
import com.qiscus.sdk.chat.core.data.model.QParticipant;
import com.qiscus.sdk.chat.core.data.model.QiscusComment;
import com.qiscus.sdk.chat.core.data.model.QiscusNonce;
import com.qiscus.sdk.chat.core.util.QiscusTextUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created on : February 02, 2017
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
final class QiscusApiParser {

    static QiscusNonce parseNonce(JsonElement jsonElement) {
        JsonObject result = jsonElement.getAsJsonObject().get("results").getAsJsonObject();
        return new QiscusNonce(new Date(result.get("expired_at").getAsLong() * 1000L),
                result.get("nonce").getAsString());
    }

    static QAccount parseQiscusAccount(JsonElement jsonElement) {
        JsonObject jsonAccount = jsonElement.getAsJsonObject().get("results").getAsJsonObject().get("user").getAsJsonObject();
        return parseQiscusAccount(jsonAccount, true);
    }

    static QAccount parseQiscusAccount(JsonObject jsonAccount, Boolean isSelf) {
        QAccount qAccount = new QAccount();
        qAccount.setId(jsonAccount.get("email").getAsString());
        qAccount.setName(jsonAccount.get("username").getAsString());
        qAccount.setAvatarUrl(jsonAccount.get("avatar_url").getAsString());
        qAccount.setLastMessageId(jsonAccount.get("last_comment_id").getAsLong());
        qAccount.setLastSyncEventId(jsonAccount.get("last_sync_event_id").getAsLong());
        try {
            qAccount.setExtras(new JSONObject(jsonAccount.get("extras").getAsJsonObject().toString()));
        } catch (Exception ignored) {
            //Do nothing
        }
        if (isSelf) {
            qAccount.setToken(jsonAccount.get("token").getAsString());
        }
        return qAccount;
    }

    static QChatRoom parseQiscusChatRoom(JsonElement jsonElement) {
        if (jsonElement != null) {
            JsonObject jsonChatRoom = jsonElement.getAsJsonObject().get("results").getAsJsonObject().get("room").getAsJsonObject();
            QChatRoom qChatRoom = new QChatRoom();
            qChatRoom.setId(jsonChatRoom.get("id").getAsLong());
            String type = "single";
            type = jsonChatRoom.get("chat_type").getAsString();
            if (type.equals("group")){
                qChatRoom.setType("group");
                if (jsonChatRoom.has("is_public_channel")) {
                    if (jsonChatRoom.get("is_public_channel").getAsBoolean() == true){
                        qChatRoom.setType("channel");
                    }
                }
            }else{
                qChatRoom.setType("single");
            }

            qChatRoom.setName(jsonChatRoom.get("room_name").getAsString());

            if (qChatRoom.getType().equals("group")) {
                qChatRoom.setDistinctId(jsonChatRoom.get("unique_id").getAsString());
            } else {
                qChatRoom.setDistinctId(jsonChatRoom.get("raw_room_name").getAsString());
            }

            qChatRoom.setUniqueId(jsonChatRoom.get("unique_id").getAsString());
            try {
                qChatRoom.setExtras(jsonChatRoom.get("options").isJsonNull() ? null :
                        new JSONObject(jsonChatRoom.get("options").getAsString()));
            } catch (JSONException ignored) {
                //Do nothing
            }
            qChatRoom.setAvatarUrl(jsonChatRoom.get("avatar_url").getAsString());

            if (jsonChatRoom.has("room_total_participants")) {
                qChatRoom.setTotalParticipants(jsonChatRoom.get("room_total_participants").getAsInt());
            }

            JsonElement participants = jsonChatRoom.get("participants");
            List<QParticipant> members = new ArrayList<>();
            if (participants.isJsonArray()) {
                JsonArray jsonMembers = participants.getAsJsonArray();
                for (JsonElement jsonMember : jsonMembers) {
                    members.add(parseQiscusRoomMember(jsonMember.getAsJsonObject()));
                }
            }
            qChatRoom.setParticipants(members);

            JsonArray comments = jsonElement.getAsJsonObject().get("results")
                    .getAsJsonObject().get("comments").getAsJsonArray();

            if (comments.size() > 0) {
                QiscusComment latestComment = parseQiscusComment(comments.get(0), qChatRoom.getId());
                qChatRoom.setLastMessage(latestComment);
            }
            return qChatRoom;
        }

        return null;
    }

    static QParticipant parseQiscusRoomMember(JsonObject jsonMember) {
        QParticipant member = new QParticipant();
        member.setId(jsonMember.get("email").getAsString());
        member.setName(jsonMember.get("username").getAsString());
        if (jsonMember.has("avatar_url")) {
            member.setAvatarUrl(jsonMember.get("avatar_url").getAsString());
        }

        try {
            if (jsonMember.has("extras")) {
                member.setExtras(new JSONObject(jsonMember.get("extras").getAsJsonObject().toString()));
            }
        } catch (JSONException ignored) {
            //Do nothing
        }

        if (jsonMember.getAsJsonObject().has("last_comment_received_id")) {
            member.setLastMessageDeliveredId(jsonMember.getAsJsonObject().get("last_comment_received_id").getAsInt());
        }
        if (jsonMember.getAsJsonObject().has("last_comment_read_id")) {
            member.setLastMessageReadId(jsonMember.getAsJsonObject().get("last_comment_read_id").getAsInt());
        }
        return member;
    }

    static List<QChatRoom> parseQiscusChatRoomInfo(JsonElement jsonElement) {
        List<QChatRoom> qChatRooms = new ArrayList<>();
        if (jsonElement != null) {
            JsonArray jsonRoomInfo = jsonElement.getAsJsonObject()
                    .get("results").getAsJsonObject().get("rooms_info").getAsJsonArray();
            for (JsonElement item : jsonRoomInfo) {
                JsonObject jsonChatRoom = item.getAsJsonObject();
                QChatRoom qChatRoom = new QChatRoom();
                qChatRoom.setId(jsonChatRoom.get("id").getAsLong());
                qChatRoom.setName(jsonChatRoom.get("room_name").getAsString());

                String type = "single";
                type = jsonChatRoom.get("chat_type").getAsString();
                if (type.equals("group")){
                    qChatRoom.setType("group");
                    if (jsonChatRoom.has("is_public_channel")) {
                        if (jsonChatRoom.get("is_public_channel").getAsBoolean() == true){
                            qChatRoom.setType("channel");
                        }
                    }
                }else{
                    qChatRoom.setType("single");
                }

                if (qChatRoom.getType().equals("group")) {
                    qChatRoom.setDistinctId(jsonChatRoom.get("unique_id").getAsString());
                } else {
                    qChatRoom.setDistinctId(jsonChatRoom.get("raw_room_name").getAsString());
                }

                qChatRoom.setUniqueId(jsonChatRoom.get("unique_id").getAsString());
                try {
                    qChatRoom.setExtras(jsonChatRoom.get("extras").isJsonNull() ? null :
                            new JSONObject(jsonChatRoom.get("extras").getAsString()));
                } catch (JSONException ignored) {
                    //Do nothing
                }
                qChatRoom.setAvatarUrl(jsonChatRoom.get("avatar_url").getAsString());
                qChatRoom.setUnreadCount(jsonChatRoom.get("unread_count").getAsInt());

                if (jsonChatRoom.has("room_total_participants")) {
                    qChatRoom.setTotalParticipants(jsonChatRoom.get("room_total_participants").getAsInt());
                }

                List<QParticipant> members = new ArrayList<>();
                if (jsonChatRoom.has("participants") && jsonChatRoom.get("participants").isJsonArray()) {
                    JsonArray jsonMembers = jsonChatRoom.get("participants").getAsJsonArray();
                    for (JsonElement jsonMember : jsonMembers) {
                        QParticipant member = new QParticipant();
                        member.setId(jsonMember.getAsJsonObject().get("email").getAsString());
                        member.setAvatarUrl(jsonMember.getAsJsonObject().get("avatar_url").getAsString());
                        member.setName(jsonMember.getAsJsonObject().get("username").getAsString());
                        if (jsonMember.getAsJsonObject().has("last_comment_received_id")) {
                            member.setLastMessageDeliveredId(jsonMember.getAsJsonObject().get("last_comment_received_id").getAsLong());
                        }
                        if (jsonMember.getAsJsonObject().has("last_comment_read_id")) {
                            member.setLastMessageReadId(jsonMember.getAsJsonObject().get("last_comment_read_id").getAsLong());
                        }
                        members.add(member);
                    }
                }
                qChatRoom.setParticipants(members);

                QiscusComment latestComment = parseQiscusComment(jsonChatRoom.get("last_comment"), qChatRoom.getId());
                qChatRoom.setLastMessage(latestComment);

                qChatRooms.add(qChatRoom);
            }
            return qChatRooms;
        }
        return qChatRooms;
    }

    static Pair<QChatRoom, List<QiscusComment>> parseQiscusChatRoomWithComments(JsonElement jsonElement) {
        if (jsonElement != null) {
            QChatRoom qChatRoom = parseQiscusChatRoom(jsonElement);

            JsonArray comments = jsonElement.getAsJsonObject().get("results").getAsJsonObject().get("comments").getAsJsonArray();
            List<QiscusComment> qiscusComments = new ArrayList<>();
            for (JsonElement jsonComment : comments) {
                qiscusComments.add(parseQiscusComment(jsonComment, qChatRoom.getId()));
            }

            return Pair.create(qChatRoom, qiscusComments);
        }

        return null;
    }

    static QiscusComment parseQiscusComment(JsonElement jsonElement, long roomId) {
        QiscusComment qiscusComment = new QiscusComment();
        JsonObject jsonComment = jsonElement.getAsJsonObject();
        qiscusComment.setRoomId(roomId);
        qiscusComment.setId(jsonComment.get("id").getAsLong());
        qiscusComment.setCommentBeforeId(jsonComment.get("comment_before_id").getAsLong());
        qiscusComment.setMessage(jsonComment.get("message").getAsString());
        qiscusComment.setSender(jsonComment.get("username").getAsString());
        qiscusComment.setSenderEmail(jsonComment.get("email").getAsString());
        qiscusComment.setSenderAvatar(jsonComment.get("user_avatar_url").getAsString());
        determineCommentState(qiscusComment, jsonComment.get("status").getAsString());

        //timestamp is in nano seconds format, convert it to milliseconds by divide it
        long timestamp = jsonComment.get("unix_nano_timestamp").getAsLong() / 1000000L;
        qiscusComment.setTime(new Date(timestamp));

        if (jsonComment.has("is_deleted")) {
            qiscusComment.setDeleted(jsonComment.get("is_deleted").getAsBoolean());
        }

        if (jsonComment.has("room_name")) {
            qiscusComment.setRoomName(jsonComment.get("room_name").getAsString());
        }

        if (jsonComment.has("room_type")) {
            qiscusComment.setGroupMessage(!"single".equals(jsonComment.get("room_type").getAsString()));
        }

        if (jsonComment.has("unique_id")) {
            qiscusComment.setUniqueId(jsonComment.get("unique_id").getAsString());
        } else if (jsonComment.has("unique_temp_id")) {
            qiscusComment.setUniqueId(jsonComment.get("unique_temp_id").getAsString());
        } else {
            qiscusComment.setUniqueId(String.valueOf(qiscusComment.getId()));
        }

        if (jsonComment.has("type")) {
            qiscusComment.setRawType(jsonComment.get("type").getAsString());
            qiscusComment.setExtraPayload(jsonComment.get("payload").toString());
            if (qiscusComment.getType() == QiscusComment.Type.BUTTONS
                    || qiscusComment.getType() == QiscusComment.Type.REPLY
                    || qiscusComment.getType() == QiscusComment.Type.CARD) {
                JsonObject payload = jsonComment.get("payload").getAsJsonObject();
                if (payload.has("text")) {
                    String text = payload.get("text").getAsString();
                    if (QiscusTextUtil.isNotBlank(text)) {
                        qiscusComment.setMessage(text.trim());
                    }
                }
            }
        }

        if (jsonComment.has("extras") && !jsonComment.get("extras").isJsonNull()) {
            try {
                qiscusComment.setExtras(new JSONObject(jsonComment.get("extras").getAsJsonObject().toString()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return qiscusComment;
    }

    private static void determineCommentState(QiscusComment qiscusComment, String status) {
        qiscusComment.setState(QiscusComment.STATE_ON_QISCUS);
        if (status != null && !status.isEmpty()) {
            switch (status) {
                case "sent":
                    qiscusComment.setState(QiscusComment.STATE_ON_QISCUS);
                    break;
                case "delivered":
                    qiscusComment.setState(QiscusComment.STATE_DELIVERED);
                    break;
                case "read":
                    qiscusComment.setState(QiscusComment.STATE_READ);
                    break;
            }
        }
    }

    public static HashMap<String, List<QParticipant>> parseQiscusCommentInfo(JsonObject jsonResults) {
        HashMap<String, List<QParticipant>> commentInfo = new HashMap<>();
        List<QParticipant> listDeliveredTo = new ArrayList<>();
        List<QParticipant> listPending = new ArrayList<>();
        List<QParticipant> listReadBy = new ArrayList<>();

        JsonArray arrDeliveredTo = jsonResults.getAsJsonArray("delivered_to");
        JsonArray arrPending = jsonResults.getAsJsonArray("pending");
        JsonArray arrReadBy = jsonResults.getAsJsonArray("read_by");

        parseMemberAndAddToList(listDeliveredTo, arrDeliveredTo);
        parseMemberAndAddToList(listDeliveredTo, arrPending);
        parseMemberAndAddToList(listDeliveredTo, arrReadBy);

        commentInfo.put("delivered_to", listDeliveredTo);
        commentInfo.put("sent", listPending); // karena pending yang dimaksud sudah masuk server qiscus
        commentInfo.put("read_by", listReadBy);

        return commentInfo;
    }

    private static void parseMemberAndAddToList(List<QParticipant> memberList, JsonArray arr) {
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            memberList.add(parseQiscusRoomMember(el.getAsJsonObject().getAsJsonObject("user")));
        }
    }
}
