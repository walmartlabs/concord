package com.walmartlabs.concord.agent.remote;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiClientFactory {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";

    private final ServerConfiguration cfg;

    private final DefaultApiClientFactory clientFactory;

    @Inject
    public ApiClientFactory(ServerConfiguration cfg) throws Exception {
        this.cfg = cfg;
        this.clientFactory = new DefaultApiClientFactory(cfg.getApiBaseUrl(), Duration.of(cfg.getConnectTimeout(), ChronoUnit.MILLIS), cfg.isVerifySsl());
    }

    public ApiClient create(String sessionToken) throws IOException {
        ImmutableApiClientConfiguration.Builder clientCfgBuilder = ApiClientConfiguration.builder()
                .baseUrl(cfg.getApiBaseUrl());

        if (sessionToken != null) {
            clientCfgBuilder.sessionToken(sessionToken);
        } else {
            clientCfgBuilder.apiKey(cfg.getApiKey());
        }

        ApiClient client = clientFactory.create(clientCfgBuilder.build())
                .setReadTimeout(Duration.of(cfg.getReadTimeout(), ChronoUnit.MILLIS))
                .setUserAgent(cfg.getUserAgent());

        Map<String, String> cookieJar = new HashMap<>();
        client.setResponseInterceptor(response -> {
            List<String> cookies = response.headers().allValues("Set-Cookie");
            if (cookies.isEmpty()) {
                return;
            }

            for (String cookie : cookies) {
                if (cookie.startsWith(SESSION_COOKIE_NAME)) {
                    cookieJar.put(SESSION_COOKIE_NAME, cookie);
                }
            }
        });

        client.setRequestInterceptor(builder -> {
            for (Map.Entry<String, String> cookie : cookieJar.entrySet()) {
                builder.header("Cookie", cookie.getValue());
            }
        });

        return client;
    }
}