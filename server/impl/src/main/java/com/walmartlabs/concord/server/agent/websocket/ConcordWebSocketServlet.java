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
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServletFactory;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;

@WebServlet("/websocket")
public class ConcordWebSocketServlet extends JettyWebSocketServlet {

    private static final long serialVersionUID = 1L;

    private final MessageChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;

    @Inject
    public ConcordWebSocketServlet(MessageChannelManager channelManager, ApiKeyDao apiKeyDao) {
        this.channelManager = channelManager;
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    public void configure(JettyWebSocketServletFactory factory) {
        factory.setCreator(new WebSocketCreator(channelManager, apiKeyDao));
    }
}
