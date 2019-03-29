package com.walmartlabs.concord.runner;

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

import com.squareup.okhttp.OkHttpClient;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {

    private static final String CONNECT_TIMEOUT_KEY = "api.connect.timeout";
    private static final String READ_TIMEOUT_KEY = "api.read.timeout";

    private final ApiConfiguration cfg;
    private final Path tmpDir;
    private final OkHttpClient httpClient;

    @Inject
    public ApiClientFactoryImpl(ApiConfiguration cfg) throws Exception {
        this.cfg = cfg;
        this.tmpDir = IOUtils.createTempDir("task-client");

        OkHttpClient client = new OkHttpClient();

        // init the SSL socket factory early to save time on the first request
        client = withSslSocketFactory(client);

        int connectTimeout = Integer.parseInt(getProperty(CONNECT_TIMEOUT_KEY, "10000"));
        int readTimeout = Integer.parseInt(getProperty(READ_TIMEOUT_KEY, "60000"));

        client.setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        client.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(30, TimeUnit.SECONDS);

        this.httpClient = client;
    }

    @Override
    public ApiClient create(ApiClientConfiguration overrides) {
        String baseUrl = overrides.baseUrl() != null ? overrides.baseUrl() : cfg.getBaseUrl();

        String sessionToken = null;
        if (overrides.apiKey() == null) {
            sessionToken = overrides.sessionToken();

            Context ctx = overrides.context();
            if (sessionToken == null && ctx != null) {
                sessionToken = cfg.getSessionToken(ctx);
            }
        }

        String apiKey = overrides.apiKey();
        if (apiKey != null) {
            sessionToken = null;
        }

        if (sessionToken == null && apiKey == null) {
            throw new IllegalArgumentException("Session token or an API key is required");
        }

        return new ConcordApiClient(baseUrl, httpClient)
                .setSessionToken(sessionToken)
                .setApiKey(apiKey)
                .addDefaultHeader("Accept", "*/*")
                .setTempFolderPath(tmpDir.toString());
    }

    private static String getProperty(String key, String def) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException(key + " must be specified");
    }

    private static OkHttpClient withSslSocketFactory(OkHttpClient client) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        return client.setSslSocketFactory(sslSocketFactory);
    }
}
