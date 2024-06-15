package com.walmartlabs.concord.client2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class DefaultApiClientFactory {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String defaultApiUrl;

    public DefaultApiClientFactory(String defaultApiUrl) {
        this(defaultApiUrl, null, true);
    }

    public DefaultApiClientFactory(String defaultApiUrl, Duration connectTimeout) {
        this(defaultApiUrl, connectTimeout, true);
    }

    public DefaultApiClientFactory(String defaultApiUrl, Duration connectTimeout, boolean verifySsl) {
        this.defaultApiUrl = defaultApiUrl;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT)
                .sslContext(sslContext(verifySsl))
                .build();
    }

    public ApiClient create() {
        return new ConcordApiClient(httpClient)
                .setBaseUrl(defaultApiUrl);
    }

    public ApiClient create(ApiClientConfiguration overrides) {
        String baseUrl = overrides.baseUrl() != null ? overrides.baseUrl() : defaultApiUrl;

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

        return new ConcordApiClient(httpClient)
                .setBaseUrl(baseUrl)
                .setSessionToken(sessionToken)
                .setApiKey(apiKey)
                .addDefaultHeader("Accept", "*/*");
    }

    private static SSLContext sslContext(boolean verifySsl) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (verifySsl) {
                sslContext.init(null, null, null);
            } else {
                TrustManager trustAll = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                };
                TrustManager[] trustManagers = new TrustManager[]{trustAll};
                System.getProperties().setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());

                sslContext.init(null, trustManagers, new SecureRandom());
            }
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
