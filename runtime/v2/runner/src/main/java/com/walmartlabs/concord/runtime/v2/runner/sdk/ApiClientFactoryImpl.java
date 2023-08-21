package com.walmartlabs.concord.runtime.v2.runner.sdk;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.UUID;

@Named
@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {

    private final ApiConfiguration cfg;
    private final InstanceId instanceId;

    @Inject
    public ApiClientFactoryImpl(ApiConfiguration cfg, InstanceId instanceId) {
        this.cfg = cfg;
        this.instanceId = instanceId;
    }

    @Override
    public ApiClient create(ApiClientConfiguration overrides) {
        String baseUrl = overrides.baseUrl() != null ? overrides.baseUrl() : cfg.baseUrl();

        String sessionToken = null;
        if (overrides.apiKey() == null) {
            sessionToken = overrides.sessionToken();
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

        UUID txId = instanceId.getValue();
        if (txId != null) {
            client = client.setUserAgent("Concord-Runner-v2: txId=" + txId);
        }

        return client;
    }

    // TODO: brig
//    private static OkHttpClient withSslSocketFactory(OkHttpClient client) throws NoSuchAlgorithmException, KeyManagementException {
//        SSLContext sslContext = SSLContext.getInstance("TLS");
//        sslContext.init(null, null, null);
//        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
//        return client.setSslSocketFactory(sslSocketFactory);
//    }
}
