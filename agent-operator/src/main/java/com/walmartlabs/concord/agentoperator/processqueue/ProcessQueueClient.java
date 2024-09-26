package com.walmartlabs.concord.agentoperator.processqueue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

public class ProcessQueueClient {

    private static final TypeReference<List<ProcessQueueEntry>> LIST_OF_PROCESS_QUEUE_ENTRIES = new TypeReference<>() {
    };

    private final String baseUrl;
    private final String apiToken;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public ProcessQueueClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.objectMapper = new ObjectMapper();
        this.client = initClient();
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(queryUrl.toString()))
                .header("Authorization", apiToken)
                .header("User-Agent", "k8s-agent-operator")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching the queue data", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Error while fetching the process queue data: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), LIST_OF_PROCESS_QUEUE_ENTRIES);
    }

    private static HttpClient initClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .cookieHandler(cookieManager)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Error while initializing the HTTP client", e);
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
