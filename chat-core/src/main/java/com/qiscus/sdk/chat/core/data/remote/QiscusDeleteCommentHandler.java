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

import androidx.annotation.RestrictTo;

import com.qiscus.sdk.chat.core.QiscusCore;
import com.qiscus.sdk.chat.core.data.model.QChatRoom;
import com.qiscus.sdk.chat.core.data.model.QMessage;
import com.qiscus.sdk.chat.core.data.model.QParticipant;
import com.qiscus.sdk.chat.core.event.QMessageDeletedEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created on : February 08, 2018
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class QiscusDeleteCommentHandler {

    private QiscusCore qiscusCore;

    public QiscusDeleteCommentHandler(QiscusCore qiscusCore) {
        this.qiscusCore = qiscusCore;
    }

    public void handle(DeletedCommentsData deletedCommentsData) {
        if (deletedCommentsData.isHardDelete()) {
            handleHardDelete(deletedCommentsData);
        } else {
            handleSoftDelete(deletedCommentsData);
        }
    }

    private void handleSoftDelete(DeletedCommentsData deletedCommentsData) {
        Observable.from(deletedCommentsData.getDeletedComments())
                .map(deletedComment -> {
                    QMessage qMessage = qiscusCore.getDataStore().getComment(deletedComment.getCommentUniqueId());
                    if (qMessage != null) {
                        qMessage.setText("This message has been deleted.");
                        qMessage.setRawType("text");
                    }
                    return qMessage;
                })
                .filter(qMessage -> qMessage != null)
                .doOnNext(qMessage -> {
                    qiscusCore.getDataStore().addOrUpdate(qMessage);
                    qiscusCore.getDataStore().deleteLocalPath(qMessage.getId());

                    EventBus.getDefault().post(new QMessageDeletedEvent(qMessage));
                })
                .toList()
                .doOnNext(qiscusComments -> {
                    if (qiscusCore.getChatConfig().getDeleteMessageListener() != null) {
                        qiscusCore.getChatConfig().getDeleteMessageListener()
                                .onHandleDeletedMessageNotification(qiscusCore.getApps(),
                                        qiscusComments, false);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(comments -> {
                }, qiscusCore.getErrorLogger()::print);
    }

    private void handleHardDelete(DeletedCommentsData deletedCommentsData) {
        Observable.from(deletedCommentsData.getDeletedComments())
                .map(deletedComment -> {
                    QMessage qMessage = qiscusCore.getDataStore().getComment(deletedComment.getCommentUniqueId());
                    if (qMessage != null) {
                        qMessage.setText("This message has been deleted.");
                        qMessage.setRawType("text");
                    }

                    return qMessage;
                })
                .filter(qMessage -> qMessage != null)
                .doOnNext(qMessage -> {
                    // Update chaining id and before id
                    QMessage commentAfter = qiscusCore.getDataStore().getCommentByBeforeId(qMessage.getId());
                    if (commentAfter != null) {
                        commentAfter.setPreviousMessageId(qMessage.getPreviousMessageId());
                        qiscusCore.getDataStore().addOrUpdate(commentAfter);
                    }

                    qiscusCore.getDataStore().addOrUpdate(qMessage);
                    qiscusCore.getDataStore().deleteLocalPath(qMessage.getId());
                    EventBus.getDefault().post(new QMessageDeletedEvent(qMessage, true));
                })
                .toList()
                .doOnNext(qiscusComments -> {
                    if (qiscusCore.getChatConfig().getDeleteMessageListener() != null) {
                        qiscusCore.getChatConfig().getDeleteMessageListener()
                                .onHandleDeletedMessageNotification(qiscusCore.getApps(),
                                        qiscusComments, true);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(comments -> {
                }, qiscusCore.getErrorLogger()::print);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static class DeletedCommentsData {
        private QParticipant actor;
        private boolean hardDelete;
        private List<DeletedComment> deletedComments;

        public QParticipant getActor() {
            return actor;
        }

        public void setActor(QParticipant actor) {
            this.actor = actor;
        }

        public boolean isHardDelete() {
            return hardDelete;
        }

        public void setHardDelete(boolean hardDelete) {
            this.hardDelete = hardDelete;
        }

        public List<DeletedComment> getDeletedComments() {
            return deletedComments;
        }

        public void setDeletedComments(List<DeletedComment> deletedComments) {
            this.deletedComments = deletedComments;
        }

        @Override
        public String toString() {
            return "DeletedCommentsData{" +
                    "actor=" + actor +
                    ", hardDelete=" + hardDelete +
                    ", deletedComments=" + deletedComments +
                    '}';
        }

        public static class DeletedComment {
            private long roomId;
            private String commentUniqueId;

            public DeletedComment(long roomId, String commentUniqueId) {
                this.roomId = roomId;
                this.commentUniqueId = commentUniqueId;
            }

            public long getRoomId() {
                return roomId;
            }

            public String getCommentUniqueId() {
                return commentUniqueId;
            }

            @Override
            public String toString() {
                return "DeletedComment{" +
                        "roomId=" + roomId +
                        ", commentUniqueId='" + commentUniqueId + '\'' +
                        '}';
            }
        }
    }
}
