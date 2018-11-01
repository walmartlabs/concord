package com.walmartlabs.concord.server.queueclient;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;

import java.util.Map;

public final class MessageSerializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static <E extends Message> E deserialize(String msg) {
        try {
            Map<String, Object> m = objectMapper.readValue(msg, Map.class);
            String messageType = (String) m.get("messageType");
            if (messageType == null) {
                throw new IllegalStateException("messageType not found: " + m);
            }
            return (E)objectMapper.convertValue(m, MessageType.valueOf(messageType).getClazz());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String serialize(Message msg) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MessageSerializer() {
    }
}
