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
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;

public class ApiClientFactoryImpl implements ApiClientFactory {

    private final ApiConfiguration cfg;

    public ApiClientFactoryImpl(ApiConfiguration cfg, Path workDir) throws Exception {
        this.cfg = cfg;
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

        ApiClient client = new ConcordApiClient(baseUrl)
                .setSessionToken(sessionToken)
                .setApiKey(apiKey)
                .addDefaultHeader("Accept", "*/*")
                .setConnectTimeout(Duration.ofMillis(cfg.connectTimeout()))
                .setReadTimeout(Duration.ofMillis(cfg.readTimeout()));
        // TODO: brig: write timeout?

        UUID txId = getTxId(overrides);

        if (txId != null) {
            client = client.setUserAgent("Concord-Runner: txId=" + txId);
        }

        return client;
    }

    @Deprecated
    private static UUID getTxId(ApiClientConfiguration cfg) {
        if (cfg.txId() != null) {
            return cfg.txId();
        }

        if (cfg.context() != null) {
            return ContextUtils.getTxId(cfg.context());
        }

        return null;
    }

    private static OkHttpClient withSslSocketFactory(OkHttpClient client) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        return client.setSslSocketFactory(sslSocketFactory);
    }
}
