package com.walmartlabs.concord.client;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.auth.ApiKeyAuth;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class RequestUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T request(ApiClient client, String uri, String method, Map<String, Object> input, Class<T> entityType) throws Exception {
        RequestBody request = input != null ? ClientUtils.createMultipartBody(input).build() : null;

        Request.Builder b = new Request.Builder()
                .url(client.getBasePath() + uri)
                .header("Accept", "*/*")
                .method(method, request);

        // we're going to use the "raw" OkHttpClient, so we need to set up the auth manually
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) client.getAuthentications().get("api_key");
        ApiKeyAuth sessionKeyAuth = (ApiKeyAuth) client.getAuthentications().get("session_key");

        if (apiKeyAuth != null && apiKeyAuth.getApiKey() != null) {
            b.header("Authorization", apiKeyAuth.getApiKey());
        } else if (sessionKeyAuth != null && sessionKeyAuth.getApiKey() != null){
            b.header(Constants.Headers.SESSION_TOKEN, sessionKeyAuth.getApiKey());
        }

        OkHttpClient ok = client.getHttpClient();
        Response resp = ok.newCall(b.build()).execute();
        assertResponse(resp);

        if (resp.code() == 204) { // HTTP "No Content"
            return null;
        }

        try (ResponseBody body = resp.body()) {
            return objectMapper.readValue(body.byteStream(), entityType);
        }
    }

    public static void assertResponse(Response resp) throws IOException {
        int code = resp.code();
        if (code < 200 || code >= 400) {
            try (ResponseBody body = resp.body()) {
                if (isJson(resp)) {
                    Object details = objectMapper.readValue(body.byteStream(), Object.class);
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
    }

    private static boolean isJson(Response resp) {
        String contentType = resp.header("Content-Type");
        if (contentType == null) {
            return false;
        }

        contentType = contentType.toLowerCase();
        return contentType.contains("json");
    }

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

    private RequestUtils() {
    }
}
