package com.walmartlabs.concord.server.agent.websocket;

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

import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.queueclient.MessageSerializer;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WebSocketPingPongListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;

public class WebSocketListener implements org.eclipse.jetty.ee8.websocket.api.WebSocketListener, WebSocketPingPongListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketListener.class);

    private final MessageChannelManager channelManager;

    private final String channelId;
    private final String agentId;
    private final String userAgent;

    public WebSocketListener(MessageChannelManager channelManager, String channelId, String agentId, String userAgent) {
        this.channelManager = requireNonNull(channelManager);
        this.channelId = requireNonNull(channelId);
        this.agentId = requireNonNull(agentId);
        this.userAgent = sanitize(userAgent);
    }

    @Override
    public void onWebSocketPing(ByteBuffer payload) {
        channelManager.getChannel(channelId, WebSocketChannel.class)
                .ifPresentOrElse(WebSocketChannel::pong,
                        () -> log.warn("onWebSocketPing ['{}'] -> channel not found", channelId));
    }

    @Override
    public void onWebSocketPong(ByteBuffer payload) {
        // we don't expect pongs
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        log.error("onWebSocketBinary ['{}'] -> not supported, closing channel", channelId);
        channelManager.close(channelId);
    }

    @Override
    public void onWebSocketText(String message) {
        channelManager.getChannel(channelId, WebSocketChannel.class)
                .ifPresentOrElse(c -> c.onRequest(MessageSerializer.deserialize(message)),
                        () -> log.warn("onWebSocketText ['{}', '{}'] -> channel not found", channelId, message));
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        channelManager.close(channelId);
        log.debug("onWebSocketClose ['{}', '{}', '{}'] -> ok", channelId, statusCode, reason);
    }

    @Override
    public void onWebSocketConnect(Session session) {
        var channel = new WebSocketChannel(channelId, agentId, session, userAgent);
        channelManager.add(channel);
        log.debug("onWebSocketConnect ['{}'] -> '{}'", channelId, userAgent);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.warn("onWebSocketError ['{}', '{}'] -> error: {}", channelId, userAgent, cause.getMessage());
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.length() > 128) {
            s = s.substring(0, 128) + "...cut";
        }
        return s.replace("\n", "\\n");
    }
}
