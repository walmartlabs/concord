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
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.auth.ApiKeyAuth;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class RequestUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void assertResponse(Response resp) throws ApiException, IOException {
        int code = resp.code();
        if (code < 200 || code >= 400) {
            try (ResponseBody body = resp.body()) {
                if (isJson(resp)) {
                    Object details = objectMapper.readValue(body.byteStream(), Object.class);
                    String msg = extractMessage(details);
                    throw new ApiException(code, msg);
                } else {
                    if (code == 401) {
                        throw new ApiException(code, "Request error: " + code + ", please verify the credentials used");
                    } else {
                        throw new ApiException(code, "Request error: " + code);
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
