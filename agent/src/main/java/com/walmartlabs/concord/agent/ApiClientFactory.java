package com.walmartlabs.concord.agent;

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

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.common.IOUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ApiClientFactory {

    private static final String SESSION_COOKIE_NAME = "JSESSIONID";

    public static ApiClient create(Configuration cfg) throws IOException {
        OkHttpClient ok = new OkHttpClient();
        ok.setReadTimeout(cfg.getReadTimeout(), TimeUnit.MILLISECONDS);
        ok.setConnectTimeout(cfg.getConnectTimeout(), TimeUnit.MILLISECONDS);

        Map<String, String> cookieJar = new HashMap<>();
        ok.interceptors().add(new AddCookiesInterceptor(cookieJar));
        ok.interceptors().add(new ReceivedCookiesInterceptor(cookieJar));

        ApiClient client = new ConcordApiClient(cfg.getServerApiBaseUrl(), ok);
        client.setTempFolderPath(IOUtils.createTempDir("agent-client").toString());
        client.setApiKey(cfg.getApiKey());
        client.setUserAgent(cfg.getUserAgent());
        client.setVerifyingSsl(cfg.isApiVerifySsl());
        return client;
    }

    private ApiClientFactory() {
    }

    private static class AddCookiesInterceptor implements Interceptor {

        private final Map<String, String> cookieJar;

        private AddCookiesInterceptor(Map<String, String> cookieJar) {
            this.cookieJar = cookieJar;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();
            for (Map.Entry<String, String> cookie : cookieJar.entrySet()) {
                builder.addHeader("Cookie", cookie.getValue());
            }
            return chain.proceed(builder.build());
        }
    }

    private static class ReceivedCookiesInterceptor implements Interceptor {

        private final Map<String, String> cookieJar;

        private ReceivedCookiesInterceptor(Map<String, String> cookieJar) {
            this.cookieJar = cookieJar;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Response resp = chain.proceed(chain.request());

            List<String> cookies = resp.headers("Set-Cookie");
            if (cookies.isEmpty()) {
                return resp;
            }

            for (String cookie : cookies) {
                if (cookie.startsWith(SESSION_COOKIE_NAME)) {
                    cookieJar.put(SESSION_COOKIE_NAME, cookie);
                }
            }

            return resp;
        }
    }
}
