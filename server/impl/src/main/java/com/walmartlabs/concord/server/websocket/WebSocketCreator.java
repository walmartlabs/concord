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

import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class WebSocketCreator implements JettyWebSocketCreator {

    private static final Logger log = LoggerFactory.getLogger(WebSocketCreator.class);

    private final WebSocketChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;

    public WebSocketCreator(WebSocketChannelManager channelManager, ApiKeyDao apiKeyDao) {
        this.channelManager = channelManager;
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
        if (channelManager.isShutdown()) {
            sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is in the maintenance mode", resp);
            return null;
        }

        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null) {
            sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing " + HttpHeaders.AUTHORIZATION + " header", resp);
            return null;
        }

        if (invalidApiKey(auth)) {
            sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API key: '" + auth + "'", resp);
            return null;
        }

        ApiKeyEntry apiKey = apiKeyDao.find(auth);
        if (apiKey == null) {
            sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API key or user not found", resp);
            return null;
        }

        UUID channelId = UUID.randomUUID();
        String agentId = req.getHeader(QueueClient.AGENT_ID);
        String userAgent = req.getHeader(QueueClient.AGENT_UA);
        return new WebSocketListener(channelManager, channelId, agentId, userAgent);
    }

    private static boolean invalidApiKey(String s) {
        try {
            Base64.getDecoder().decode(s);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private void sendError(int statusCode, String message, JettyServerUpgradeResponse resp) {
        try {
            resp.sendError(statusCode, message);
        } catch (IOException e) {
            log.error("sendError ['{}', '{}'] -> error", statusCode, message, e);
            throw new RuntimeException(e);
        }
    }
}
