package com.walmartlabs.concord.server.queueclient;

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

import java.io.Serializable;

public class QueueClientConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String[] addresses;
    private final String agentId;
    private final String apiKey;
    private final String userAgent;
    private final long connectTimeout;
    private final long pingInterval;
    private final long maxNoActivityPeriod;

    private final long processRequestDelay;
    private final long reconnectDelay;

    private QueueClientConfiguration(Builder b) {
        this.addresses = b.addresses;
        this.agentId = b.agentId;
        this.apiKey = b.apiKey;
        this.userAgent = b.userAgent;
        this.connectTimeout = b.connectTimeout;
        this.pingInterval = b.pingInterval;
        this.maxNoActivityPeriod = b.maxNoActivityPeriod;
        this.processRequestDelay = b.processRequestDelay;
        this.reconnectDelay = b.reconnectDelay;
    }

    public String[] getAddresses() {
        return addresses;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getPingInterval() {
        return pingInterval;
    }

    public long getMaxNoActivityPeriod() {
        return maxNoActivityPeriod;
    }

    public long getProcessRequestDelay() {
        return processRequestDelay;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public static class Builder {

        private final String[] addresses;

        private String agentId;
        private String apiKey;
        private String userAgent;
        private long connectTimeout = 30000;
        private long pingInterval = 10000;
        private long maxNoActivityPeriod = 30000;
        private long processRequestDelay = 1000;
        private long reconnectDelay = 10000;

        public Builder(String[] addresses) {
            this.addresses = addresses;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder connectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder pingInterval(long pingInterval) {
            this.pingInterval = pingInterval;
            return this;
        }

        public Builder maxNoActivityPeriod(long maxNoActivityPeriod) {
            this.maxNoActivityPeriod = maxNoActivityPeriod;
            return this;
        }

        public Builder processRequestDelay(long processRequestDelay) {
            this.processRequestDelay = processRequestDelay;
            return this;
        }

        public Builder reconnectDelay(long reconnectDelay) {
            this.reconnectDelay = reconnectDelay;
            return this;
        }

        public QueueClientConfiguration build() {
            return new QueueClientConfiguration(this);
        }
    }
}
