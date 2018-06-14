package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.google.inject.Provider;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;

@Named
@Singleton
public class ApiClientProvider implements Provider<ApiClient> {

    private static final Logger log = LoggerFactory.getLogger(ApiClientProvider.class);

    public static final String SERVER_API_BASE_URL_KEY = "api.baseUrl";

    public static final String API_KEY = "api.key";
    public static final String CONNECT_TIMEOUT_KEY = "api.connect.timeout";
    public static final String READ_TIMEOUT_KEY = "api.read.timeout";

    /**
     * As defined in server/db/src/main/resources/com/walmartlabs/concord/server/db/v0.70.0.xml
     */
    private static final String DEFAULT_AGENT_API_KEY = "Gz0q/DeGlH8Zs7QJMj1v8g";

    private final ApiClient apiClient;

    public ApiClientProvider() throws IOException {
        String serverApiBaseUrl = getEnv(SERVER_API_BASE_URL_KEY, "http://localhost:8001");

        String apiKey = getEnv(API_KEY, DEFAULT_AGENT_API_KEY);
        int connectTimeout = Integer.parseInt(getEnv(CONNECT_TIMEOUT_KEY, "10000"));
        int readTimeout = Integer.parseInt(getEnv(READ_TIMEOUT_KEY, "10000"));

        this.apiClient = new ConcordApiClient();
        this.apiClient.setTempFolderPath(IOUtils.createTempDir("runner-client").toString());
        this.apiClient.setBasePath(serverApiBaseUrl);
        this.apiClient.setApiKey(apiKey);
        this.apiClient.setReadTimeout(readTimeout);
        this.apiClient.setConnectTimeout(connectTimeout);

        log.info("Using the API address: {}", serverApiBaseUrl);
    }

    private static String getEnv(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }

    @Override
    public ApiClient get() {
        return apiClient;
    }
}
