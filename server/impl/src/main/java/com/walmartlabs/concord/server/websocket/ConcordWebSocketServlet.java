package com.walmartlabs.concord.server.websocket;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.annotation.WebServlet;

@Named
@WebServlet("/websocket")
public class ConcordWebSocketServlet extends WebSocketServlet {

    private static final long serialVersionUID = 1L;

    private final WebSocketChannelManager channelManager;
    private final ApiKeyDao apiKeyDao;

    @Inject
    public ConcordWebSocketServlet(WebSocketChannelManager channelManager, ApiKeyDao apiKeyDao) {
        this.channelManager = channelManager;
        this.apiKeyDao = apiKeyDao;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(new WebSocketCreator(channelManager, apiKeyDao));
    }
}
