package com.walmartlabs.concord.client2.impl;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;

public final class RequestBodyHandler {

    public static HttpRequest.BodyPublisher handle(ObjectMapper ignoredObjectMapper, byte[] param) throws IOException {
        return HttpRequest.BodyPublishers.ofByteArray(param);
    }

    public static HttpRequest.BodyPublisher handle(ObjectMapper ignoredObjectMapper, InputStream param) throws IOException {
        return HttpRequest.BodyPublishers.ofInputStream(() -> param);
    }

    public static HttpRequest.BodyPublisher handle(ObjectMapper objectMapper, Object param) throws IOException {
        if (param instanceof String) {
            return HttpRequest.BodyPublishers.ofString((String) param);
        }
        byte[] localVarPostBody = objectMapper.writeValueAsBytes(param);
        return HttpRequest.BodyPublishers.ofByteArray(localVarPostBody);
    }

    private RequestBodyHandler() {
    }
}
