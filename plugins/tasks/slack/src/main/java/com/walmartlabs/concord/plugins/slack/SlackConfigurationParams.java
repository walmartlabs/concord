package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Map;

public class SlackConfigurationParams {

    public static SlackConfigurationParams of(Variables input, Map<String, Object> defaults) {
        return new SlackConfigurationParams(Utils.merge(input, defaults));
    }

    private static final String API_TOKEN = "apiToken";
    private static final String IS_LEGACY = "isLegacy";
    private static final String AUTH_TOKEN = "authToken";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String RETRY_COUNT = "retryCount";
    private static final String SO_TIMEOUT = "soTimeout";
    private static final String PROXY_ADDRESS = "proxyAddress";
    private static final String PROXY_PORT = "proxyPort";

    protected final Variables variables;

    public SlackConfigurationParams(Variables variables) {
        this.variables = variables;
    }

    public String apiToken() {
        String apiToken = variables.getString(API_TOKEN);
        if (apiToken == null) {
            // fallback to the old "authToken" parameter
            apiToken = variables.getString(AUTH_TOKEN);
        }

        if (apiToken == null) {
            throw new IllegalStateException("'" + API_TOKEN + "' or '" + AUTH_TOKEN + "' is required.");
        }

        return apiToken;
    }

    public boolean isLegacy() {
        return variables.getBoolean(IS_LEGACY, true);
    }

    public int connectTimeout(int defaultValue) {
        return variables.getInt(CONNECT_TIMEOUT, defaultValue);
    }

    public int soTimeout(int defaultValue) {
        return variables.getInt(SO_TIMEOUT, defaultValue);
    }

    public int retryCount(int defaultValue) {
        return variables.getInt(RETRY_COUNT, defaultValue);
    }

    public String proxyAddress() {
        return variables.getString(PROXY_ADDRESS);
    }

    public int proxyPort(int defaultValue) {
        return variables.getInt(PROXY_PORT, defaultValue);
    }
}
