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
import okhttp3.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.List;

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

    public List<ProcessQueueEntry> query() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/api/v2/process?status=ENQUEUED")
                .header("Authorization", apiToken)
                .build();

        Call call = client.newCall(req);
        try (Response resp = call.execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Error while fething the process queue data: " + resp.code());
            }

            ResponseBody body = resp.body();
            if (body == null) {
                throw new IOException("Error while fething the process queue data: empty response");
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

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
