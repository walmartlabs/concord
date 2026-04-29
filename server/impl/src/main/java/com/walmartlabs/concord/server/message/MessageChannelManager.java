package com.walmartlabs.concord.server.message;

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

import com.walmartlabs.concord.server.queueclient.message.Message;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MessageChannelManager {

    private static final Logger log = LoggerFactory.getLogger(MessageChannelManager.class);

    private final Map<String, MessageChannel> channels = new ConcurrentHashMap<>();

    private volatile boolean isShutdown;

    public boolean isShutdown() {
        return isShutdown;
    }

    public void shutdown() {
        isShutdown = true;

        channels.forEach((uuid, channel) -> {
            try {
                channel.close();
            } catch (Exception e) {
                log.warn("shutdown -> failed on channel {}: {}", channel.getClass(), e.getMessage());
            }
        });
        log.info("shutdown -> done");
    }

    public void close(String channelId) {
        MessageChannel channel = channels.remove(channelId);
        if (channel == null) {
            log.warn("close ['{}'] -> channel not found", channelId);
            return;
        }

        try {
            channel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("close ['{}'] -> done", channelId);
    }

    public boolean sendMessage(String channelId, Message response) {
        MessageChannel channel = channels.get(channelId);
        if (channel == null) {
            log.warn("sendResponse ['{}', '{}'] -> channel not found", channelId, response);
            return false;
        }

        try {
            return channel.offerMessage(response);
        } catch (Exception e) {
            log.warn("sendResponse ['{}', '{}'] -> failed: {}", channelId, response, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <M extends Message> Map<MessageChannel, M> getRequests(MessageType requestType) {
        Map<MessageChannel, M> result = new HashMap<>();
        channels.forEach((channelId, channel) -> {
            try {
                channel.getMessage(requestType).ifPresent(msg -> {
                    result.put(channel, (M) msg);
                });
            } catch (Exception e) {
                log.warn("getRequests ['{}'] -> failed on channel {}: {}", requestType, channel.getClass(), e.getMessage());
            }
        });
        return result;
    }

    public void add(MessageChannel channel) {
        channels.put(channel.getChannelId(), channel);
    }

    public int connectedClientsCount() {
        return channels.size();
    }

    public Map<String, MessageChannel> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    @SuppressWarnings("unchecked")
    public <C extends MessageChannel> Optional<C> getChannel(String channelId, Class<C> klass) {
        return Optional.ofNullable(channels.get(channelId))
                .filter(c -> klass.isAssignableFrom(c.getClass()))
                .map(c -> (C) c);
    }
}
