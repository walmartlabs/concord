package com.walmartlabs.concord.agentoperator.processqueue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.walmartlabs.concord.agentoperator.scheduler.QueueSelector;
import okhttp3.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessQueueClient {

    private static final TypeReference<List<ProcessQueueEntry>> LIST_OF_PROCESS_QUEUE_ENTRIES = new TypeReference<List<ProcessQueueEntry>>() {
    };

    private final String baseUrl;
    private final String apiToken;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public ProcessQueueClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.client = initClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<ProcessQueueEntry> query(String processStatus, int limit, QueueSelector queueSelector) throws IOException {
        StringBuilder queryUrl = new StringBuilder(baseUrl + "/api/v2/process/requirements?status=" + processStatus + "&limit=" + limit + "&startAt.len=");
        String flavor = queueSelector.getFlavor();
        if (flavor != null) {
            queryUrl.append("&requirements.agent.flavor.eq=").append(flavor);
        }
        List<String> queryParams = queueSelector.getQueryParams();
        if (queryParams != null) {
            for (String queryParam : queryParams) {
                queryUrl.append("&").append(escapeQueryParam(queryParam));
            }
        }
        Request req = new Request.Builder()
                .url(queryUrl.toString())
                .header("Authorization", apiToken)
                .addHeader("User-Agent", "k8s-agent-operator")
                .build();

        Call call = client.newCall(req);
        try (Response resp = call.execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Error while fetching the process queue data: " + resp.code());
            }

            ResponseBody body = resp.body();
            if (body == null) {
                throw new IOException("Error while fetching the process queue data: empty response");
            }

            return objectMapper.readValue(body.byteStream(), LIST_OF_PROCESS_QUEUE_ENTRIES);
        }
    }

    private static OkHttpClient initClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            Map<String, String> cookieJar = new HashMap<>();
            builder.addInterceptor(new AddCookiesInterceptor(cookieJar));
            builder.addInterceptor(new ReceivedCookiesInterceptor(cookieJar));

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

        private static final String SESSION_COOKIE_NAME = "JSESSIONID";

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

    @VisibleForTesting
    static String escapeQueryParam(String s) {
        Escaper escaper = UrlEscapers.urlPathSegmentEscaper();
        int i = s.indexOf("=");
        if (i < 0) {
            return escaper.escape(s);
        }
        String key = s.substring(0, i);
        String value = s.substring(i + 1);
        return escaper.escape(key) + "=" + escaper.escape(value);
    }
}
