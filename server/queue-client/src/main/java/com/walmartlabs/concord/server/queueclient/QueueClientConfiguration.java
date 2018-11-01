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

    private final String address;
    private final String apiToken;
    private final String userAgent;
    private final long connectTimeout;
    private final long maxInactivityPeriod;

    private QueueClientConfiguration(String address, String apiToken, String userAgent, long connectTimeout, long maxInactivityPeriod) {
        this.address = address;
        this.apiToken = apiToken;
        this.userAgent = userAgent;
        this.connectTimeout = connectTimeout;
        this.maxInactivityPeriod = maxInactivityPeriod;
    }

    public String getAddress() {
        return address;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getMaxInactivityPeriod() {
        return maxInactivityPeriod;
    }

    public static class Builder {

        private final String address;

        private String apiToken;
        private String userAgent;
        private long connectTimeout = 30000;
        private long maxInactivityPeriod = 120000;

        public Builder(String address) {
            this.address = address;
        }

        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
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

        public Builder maxInactivityPeriod(long maxInactivityPeriod) {
            this.maxInactivityPeriod = maxInactivityPeriod;
            return this;
        }

        public QueueClientConfiguration build() {
            return new QueueClientConfiguration(address, apiToken, userAgent, connectTimeout, maxInactivityPeriod);
        }
    }
}
