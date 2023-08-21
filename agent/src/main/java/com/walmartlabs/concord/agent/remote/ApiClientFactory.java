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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;
import com.walmartlabs.concord.client.ConcordApiClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
@Singleton
public class ApiClientFactory {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";

    private final ServerConfiguration cfg;

    @Inject
    public ApiClientFactory(ServerConfiguration cfg) {
        this.cfg = cfg;
    }

    public ApiClient create(String sessionToken) throws IOException {

        ConcordApiClient client = new ConcordApiClient(cfg.getApiBaseUrl());
        client.setReadTimeout(Duration.of(cfg.getReadTimeout(), ChronoUnit.MILLIS));
        client.setConnectTimeout(Duration.of(cfg.getConnectTimeout(), ChronoUnit.MILLIS));
        if (sessionToken != null) {
            client.setSessionToken(sessionToken);
        } else {
            client.setApiKey(cfg.getApiKey());
        }
        client.setUserAgent(cfg.getUserAgent());
        // TODO: brig: impl
//        client.setVerifyingSsl(cfg.isVerifySsl());

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
