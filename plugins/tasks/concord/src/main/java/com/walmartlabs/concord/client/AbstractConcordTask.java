package com.walmartlabs.concord.client;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

public abstract class AbstractConcordTask implements Task {

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected static final String API_KEY = "apiKey";

    @Inject
    ApiConfiguration apiCfg;

    @Inject
    ApiClientFactory apiClientFactory;

    protected <T> T withClient(Context ctx, CheckedFunction<ApiClient, T> f) throws Exception {
        ApiClientConfiguration cfg = ApiClientConfiguration.builder()
                .baseUrl(getBaseUrl(ctx))
                .apiKey(getApiKey(ctx))
                .context(ctx)
                .build();

        return f.apply(apiClientFactory.create(cfg));
    }

    protected <T> T request(Context ctx, String uri, Map<String, Object> input, Class<T> entityType) throws Exception {
        return withClient(ctx, client -> {
            RequestBody body = ClientUtils.createMultipartBody(input).build();

            Request.Builder b = new Request.Builder()
                    .url(client.getBasePath() + uri)
                    .header("Accept", "*/*")
                    .post(body);

            // we're going to use the "raw" OkHttpClient, so we need to set up the auth manually
            String apiKey = getApiKey(ctx);
            if (apiKey != null) {
                b.header("Authorization", apiKey);
            } else {
                b.header("X-Concord-SessionToken", apiCfg.getSessionToken(ctx)); // TODO constants
            }

            OkHttpClient ok = client.getHttpClient();
            Response resp = ok.newCall(b.build()).execute();
            assertResponse(resp);

            return objectMapper.readValue(resp.body().byteStream(), entityType);
        });
    }

    protected void assertResponse(Response resp) throws IOException {
        int code = resp.code();
        if (code < 200 || code >= 400) {
            if (isJson(resp)) {
                Object details = objectMapper.readValue(resp.body().byteStream(), Object.class);
                String msg = extractMessage(details);
                throw new IOException(msg);
            } else {
                if (code == 401) {
                    throw new IOException("Request error: " + code + ", please verify the credentials used");
                } else {
                    throw new IOException("Request error: " + code);
                }
            }
        }
    }

    private String getApiKey(Context ctx) {
        return (String) ctx.getVariable(API_KEY);
    }

    // TODO move to ClientUtils?
    @SuppressWarnings("unchecked")
    private static String extractMessage(Object details) {
        if (details == null) {
            return null;
        }

        if (details instanceof List) {
            List<Object> l = (List<Object>) details;
            if (!l.isEmpty()) {
                Object o = l.get(0);
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    Object msg = m.get("message");
                    if (msg != null) {
                        return msg.toString();
                    }
                }
            }
        }

        return details.toString();
    }

    protected Map<String, Object> createCfg(Context ctx) {
        return createCfg(ctx, (String[]) null);
    }

    protected Map<String, Object> createCfg(Context ctx, String... keys) {
        Map<String, Object> m = new HashMap<>();

        String sessionToken = apiCfg.getSessionToken(ctx);
        if (sessionToken != null) {
            m.put(SESSION_TOKEN_KEY, sessionToken);
        }

        for (String k : keys) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }

        return m;
    }

    private static boolean isJson(Response resp) {
        String contentType = resp.header("Content-Type");
        if (contentType == null) {
            return false;
        }

        contentType = contentType.toLowerCase();
        return contentType.contains("json");
    }

    private static String getBaseUrl(Context ctx) {
        Object v = ctx.getVariable(Keys.BASE_URL_KEY);
        if (v == null) {
            return null;
        }

        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Expected a string value '" + Keys.BASE_URL_KEY + "', got: " + v);
        }

        return (String) v;
    }

    @FunctionalInterface
    protected interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
