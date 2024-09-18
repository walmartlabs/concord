package com.walmartlabs.concord.server.websocket;

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

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Named
@Singleton
public class WebSocketChannelManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelManager.class);

    private final Map<UUID, WebSocketChannel> channels = new ConcurrentHashMap<>();

    private volatile boolean isShutdown;

    public boolean isShutdown() {
        return isShutdown;
    }

    public void shutdown() {
        isShutdown = true;

        channels.forEach((uuid, webSocketChannel) -> webSocketChannel.close());
        log.info("shutdown -> done");
    }

    public void close(UUID channelId) {
        WebSocketChannel channel = channels.remove(channelId);
        if (channel == null) {
            log.warn("close ['{}'] -> channel not found", channelId);
            return;
        }

        channel.close();

        log.info("close ['{}'] -> done", channelId);
    }

    public void onRequest(UUID channelId, Message message) {
        WebSocketChannel channel = channels.get(channelId);
        if (channel == null) {
            log.warn("request ['{}', '{}'] -> channel not found", channelId, message);
            return;
        }

        channel.onRequest(message);
    }

    /**
     * Sends the response and removes the associated request from the queue.
     */
    public boolean sendResponse(UUID channelId, Message response) {
        WebSocketChannel channel = channels.get(channelId);
        if (channel == null) {
            log.warn("request ['{}', '{}'] -> channel not found", channelId, response);
            return false;
        }

        return channel.sendResponse(response);
    }

    public boolean pong(UUID channelId) {
        WebSocketChannel channel = channels.get(channelId);
        if (channel == null) {
            log.warn("pong ['{}'] -> channel not found", channelId);
            return false;
        }

        return channel.pong();
    }

    @SuppressWarnings("unchecked")
    public <E> Map<WebSocketChannel, E> getRequests(MessageType requestType) {
        Map<WebSocketChannel, E> result = new HashMap<>();
        channels.forEach((channelId, channel) -> {
            Message m = channel.getRequest(requestType);
            if (m != null) {
                result.put(channel, (E)m);
            }
        });
        return result;
    }

    public void add(UUID channelId, WebSocketChannel channel) {
        channels.put(channelId, channel);
    }

    public int connectedClientsCount() {
        return channels.size();
    }

    public Map<UUID, WebSocketChannel> getChannels() {
        return Collections.unmodifiableMap(channels);
    }
}
