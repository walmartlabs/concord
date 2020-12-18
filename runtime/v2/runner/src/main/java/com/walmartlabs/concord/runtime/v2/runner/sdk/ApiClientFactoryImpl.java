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

import com.squareup.okhttp.OkHttpClient;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.ApiConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class ApiClientFactoryImpl implements ApiClientFactory {

    private final ApiConfiguration cfg;
    private final InstanceId instanceId;

    private final Path tmpDir;
    private final OkHttpClient httpClient;

    @Inject
    public ApiClientFactoryImpl(ApiConfiguration cfg, InstanceId instanceId, FileService fileService) throws Exception {
        this.cfg = cfg;
        this.instanceId = instanceId;
        this.tmpDir = fileService.createTempDirectory("api-client");

        OkHttpClient client = new OkHttpClient();

        // init the SSL socket factory early to save time on the first request
        client = withSslSocketFactory(client);

        client.setConnectTimeout(cfg.connectTimeout(), TimeUnit.MILLISECONDS);
        client.setReadTimeout(cfg.readTimeout(), TimeUnit.MILLISECONDS);
        client.setWriteTimeout(30, TimeUnit.SECONDS);

        this.httpClient = client;
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

        ApiClient client = new ConcordApiClient(baseUrl, httpClient)
                .setSessionToken(sessionToken)
                .setApiKey(apiKey)
                .addDefaultHeader("Accept", "*/*")
                .setTempFolderPath(tmpDir.toString());

        UUID txId = instanceId.getValue();
        if (txId != null) {
            client = client.setUserAgent("Concord-Runner-v2: txId=" + txId);
        }

        return client;
    }

    private static OkHttpClient withSslSocketFactory(OkHttpClient client) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        return client.setSslSocketFactory(sslSocketFactory);
    }
}
