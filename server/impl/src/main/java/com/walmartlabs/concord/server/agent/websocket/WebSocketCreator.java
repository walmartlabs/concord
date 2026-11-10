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
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;

import java.util.Base64;
import java.util.UUID;

public class WebSocketCreator implements org.eclipse.jetty.websocket.server.WebSocketCreator {

    private final MessageChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;
    private final WebsocketsConfiguration cfg;
    private final UserSecurityContext userSecurityContext;

    public WebSocketCreator(
            MessageChannelManager channelManager,
            ApiKeyDao apiKeyDao,
            WebsocketsConfiguration cfg,
            UserSecurityContext userSecurityContext
    ) {
        this.channelManager = channelManager;
        this.apiKeyDao = apiKeyDao;
        this.cfg = cfg;
        this.userSecurityContext = userSecurityContext;
    }

    @Override
    public Object createWebSocket(ServerUpgradeRequest req, ServerUpgradeResponse resp, Callback callback) {
        if (channelManager.isShutdown()) {
            Response.writeError(req, resp, callback, HttpStatus.SERVICE_UNAVAILABLE_503, "Server is in the maintenance mode");
            return null;
        }

        String auth = req.getHeaders().get(HttpHeader.AUTHORIZATION);
        if (auth == null) {
            Response.writeError(req, resp, callback, HttpStatus.UNAUTHORIZED_401, "Missing " + HttpHeader.AUTHORIZATION.asString() + " header");
            return null;
        }

        if (invalidApiKey(auth)) {
            Response.writeError(req, resp, callback, HttpStatus.FORBIDDEN_403, "Invalid API key: '" + auth + "'");
            return null;
        }

        ApiKeyEntry apiKey = apiKeyDao.find(auth);
        if (apiKey == null) {
            Response.writeError(req, resp, callback, HttpStatus.FORBIDDEN_403, "Invalid API key or user not found");
            return null;
        }

        if (cfg.isRequirePermission() && !userSecurityContext.isPermitted(apiKey.getUserId(), Permission.AGENT_WEBSOCKET)) {
            Response.writeError(req, resp, callback, HttpStatus.FORBIDDEN_403, "Permission denied");
            return null;
        }

        String channelId = "ws-" + UUID.randomUUID();
        String agentId = req.getHeaders().get(QueueClient.AGENT_ID);
        String userAgent = req.getHeaders().get(QueueClient.AGENT_UA);
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

}
