package com.walmartlabs.concord.plugins.slack;

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

public class SlackConfiguration {

    public static SlackConfiguration from(SlackConfigurationParams in) {
        String apiToken = in.apiToken();
        boolean isLegacy = in.isLegacy();

        SlackConfiguration cfg = new SlackConfiguration(apiToken, isLegacy);
        cfg.setProxy(in.proxyAddress(), in.proxyPort(-1));
        cfg.setConnectTimeout(in.connectTimeout(DEFAULT_CONNECT_TIMEOUT));
        cfg.setSoTimeout(in.soTimeout(DEFAULT_SO_TIMEOUT));
        cfg.setRetryCount(in.retryCount(DEFAULT_RETRY_COUNT));
        return cfg;
    }

    private static final int DEFAULT_CONNECT_TIMEOUT = 30_000;
    private static final int DEFAULT_SO_TIMEOUT = 30_000;
    private static final int DEFAULT_RETRY_COUNT = 3;

    private final String authToken;
    private final boolean isLegacy;
    private String proxyAddress;
    private Integer proxyPort;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int soTimeout = DEFAULT_SO_TIMEOUT;
    private int retryCount = DEFAULT_RETRY_COUNT;

    public SlackConfiguration(String authToken) {
        this(authToken, false);
    }

    public SlackConfiguration(String authToken, boolean isLegacy) {
        this.authToken = authToken;
        this.isLegacy = isLegacy;
    }

    private void setProxy(String proxyAddress, Integer proxyPort) {
        this.proxyAddress = proxyAddress;
        this.proxyPort = proxyPort;
    }

    private void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    private void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    private void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean isLegacy() {
        return isLegacy;
    }

    public String getProxyAddress() {
        return proxyAddress;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
