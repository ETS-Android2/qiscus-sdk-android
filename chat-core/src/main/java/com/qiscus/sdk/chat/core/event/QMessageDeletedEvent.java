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

package com.qiscus.sdk.chat.core.event;

import com.qiscus.sdk.chat.core.data.model.QMessage;

/**
 * Created on : February 12, 2018
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public class QMessageDeletedEvent {
    private QMessage qiscusMessage;
    private boolean hardDelete;

    public QMessageDeletedEvent(QMessage qiscusMessage) {
        this.qiscusMessage = qiscusMessage;
    }

    public QMessageDeletedEvent(QMessage qiscusMessage, boolean hardDelete) {
        this.qiscusMessage = qiscusMessage;
        this.hardDelete = hardDelete;
    }

    public QMessage getQMessage() {
        return qiscusMessage;
    }

    public boolean isHardDelete() {
        return hardDelete;
    }
}
