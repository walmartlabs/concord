package com.walmartlabs.concord.agent.cfg;

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

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.agent.cfg.Utils.getStringOrDefault;

public class ServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServerConfiguration.class);

    private final String apiBaseUrl;
    private final String[] websocketUrls;
    private final String apiKey;
    private final long pingInterval;
    private final long maxNoActivityPeriod;
    private final boolean verifySsl;
    private final long connectTimeout;
    private final long readTimeout;
    private final String userAgent;
    private final long maxNoHeartbeatInterval;
    private final long processRequestDelay;
    private final long reconnectDelay;

    @Inject
    public ServerConfiguration(Config cfg, AgentConfiguration agentCfg) {
        this.apiBaseUrl = cfg.getString("server.apiBaseUrl");
        log.info("Using the Server's API address: {}", apiBaseUrl);

        this.websocketUrls = getWebsocketUrls(cfg);
        log.info("Using the Server's websocket addresses: {}", (Object[]) websocketUrls);

        this.apiKey = cfg.getString("server.apiKey");
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Configuration is missing value for server.apiKey!");
        }

        this.pingInterval = cfg.getDuration("server.websocketPingInterval", TimeUnit.MILLISECONDS);
        this.maxNoActivityPeriod = cfg.getDuration("server.websocketMaxNoActivityPeriod", TimeUnit.MILLISECONDS);

        this.verifySsl = cfg.getBoolean("server.verifySsl");

        this.connectTimeout = cfg.getDuration("server.connectTimeout", TimeUnit.MILLISECONDS);
        this.readTimeout = cfg.getDuration("server.readTimeout", TimeUnit.MILLISECONDS);
        this.userAgent = getStringOrDefault(cfg, "server.userAgent", () -> "Concord-Agent: id=" + agentCfg.getAgentId());

        this.maxNoHeartbeatInterval = cfg.getDuration("server.maxNoHeartbeatInterval", TimeUnit.MILLISECONDS);

        this.processRequestDelay = cfg.getDuration("server.processRequestDelay", TimeUnit.MILLISECONDS);
        this.reconnectDelay = cfg.getDuration("server.reconnectDelay", TimeUnit.MILLISECONDS);
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String[] getWebsocketUrls() {
        return websocketUrls;
    }

    public String getApiKey() {
        return apiKey;
    }

    public long getPingInterval() {
        return pingInterval;
    }

    public long getMaxNoActivityPeriod() {
        return maxNoActivityPeriod;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public long getMaxNoHeartbeatInterval() {
        return maxNoHeartbeatInterval;
    }

    public long getProcessRequestDelay() {
        return processRequestDelay;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    private static String[] getWebsocketUrls(Config cfg) {
        // we had a silly typo ("websockeR") in our configs, so for backward compatibility we must check the old variant first
        String oldKey = "server.websockerUrl";
        if (cfg.hasPath(oldKey)) {
            String[] as = getCSV(cfg.getString(oldKey));
            if (as != null) {
                return as;
            }
        }

        return getCSV(cfg.getString("server.websocketUrl"));
    }

    private static String[] getCSV(String s) {
        if (s == null) {
            return null;
        }

        String[] as = s.split(",");
        for (int i = 0; i < as.length; i++) {
            as[i] = as[i].trim();
        }

        return as;
    }
}
