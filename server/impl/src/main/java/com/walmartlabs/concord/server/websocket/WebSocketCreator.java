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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class WebSocketCreator implements org.eclipse.jetty.websocket.servlet.WebSocketCreator {

    private static final Logger log = LoggerFactory.getLogger(WebSocketCreator.class);

    private final WebSocketChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;

    public WebSocketCreator(WebSocketChannelManager channelManager, ApiKeyDao apiKeyDao) {
        this.channelManager = channelManager;
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
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
            sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API token: '" + auth + "'", resp);
            return null;
        }

        UUID user = apiKeyDao.findUserId(auth);
        if (user == null) {
            sendError(HttpServletResponse.SC_FORBIDDEN, "User with key '" + auth + "' not found", resp);
            return null;
        }

        UUID channelId = UUID.randomUUID();
        String channelInfo = req.getHeader(InternalConstants.Headers.AGENT);
        return new WebSocketListener(channelManager, channelId, channelInfo);
    }

    private static boolean invalidApiKey(String s) {
        try {
            Base64.getDecoder().decode(s);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private void sendError(int statusCode, String message, ServletUpgradeResponse resp) {
        try {
            resp.sendError(statusCode, message);
        } catch (IOException e) {
            log.error("sendError ['{}', '{}'] -> error", statusCode, message, e);
            throw new RuntimeException(e);
        }
    }
}
