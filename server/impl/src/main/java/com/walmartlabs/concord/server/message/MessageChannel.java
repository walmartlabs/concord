package com.walmartlabs.concord.server.message;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;

import java.io.Closeable;
import java.util.Optional;

/**
 * Represents an open message channel between the server and a remote agent.
 */
public interface MessageChannel extends Closeable {

    /**
     * A unique identifier of a channel.
     */
    String getChannelId();

    /**
     * An identifier of the agent represented by this MessageChannel.
     */
    String getAgentId();

    /**
     * Attempts to send a message.
     * @return {@code true} if the message was sent successfully.
     * Returns {@code false} if the message cannot be sent at the moment.
     * @apiNote The implementors must expect the server to re-attempt
     * the sending of the same message.
     */
    boolean offerMessage(Message msg) throws Exception;

    /**
     * Attempts to grab a received message.
     * @return an empty value if no messages of the specified type can be returned
     * at the moment.
     */
    Optional<Message> getMessage(MessageType messageType) throws Exception;
}
