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

import com.walmartlabs.concord.server.boot.WebSocketConfigurator;
import com.walmartlabs.concord.server.message.MessageChannelManager;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.eclipse.jetty.websocket.server.ServerWebSocketContainer;

import javax.inject.Inject;

public class AgentWebSocketConfigurator implements WebSocketConfigurator {

    public static final String PATH = "/websocket";

    private final MessageChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;
    private final UserSecurityContext userSecurityContext;
    private final WebsocketsConfiguration cfg;

    @Inject
    public AgentWebSocketConfigurator(
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
    public void configure(ServerWebSocketContainer container) {
        container.addMapping(PATH, new WebSocketCreator(channelManager, apiKeyDao, cfg, userSecurityContext));
    }
}
