package com.walmartlabs.concord.agent.remote;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.QueueClientConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URISyntaxException;

public class QueueClientProvider implements Provider<QueueClient> {

    private final AgentConfiguration agentCfg;
    private final ServerConfiguration serverCfg;

    @Inject
    public QueueClientProvider(AgentConfiguration agentCfg, ServerConfiguration serverCfg) {
        this.agentCfg = agentCfg;
        this.serverCfg = serverCfg;
    }

    @Override
    public QueueClient get() {
        try {
            QueueClient queueClient = new QueueClient(new QueueClientConfiguration.Builder(serverCfg.getWebsocketUrls())
                    .agentId(agentCfg.getAgentId())
                    .apiKey(serverCfg.getApiKey())
                    .userAgent(serverCfg.getUserAgent())
                    .connectTimeout(serverCfg.getConnectTimeout())
                    .pingInterval(serverCfg.getPingInterval())
                    .maxNoActivityPeriod(serverCfg.getMaxNoActivityPeriod())
                    .processRequestDelay(serverCfg.getProcessRequestDelay())
                    .reconnectDelay(serverCfg.getReconnectDelay())
                    .build());

            queueClient.start();

            return queueClient;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
