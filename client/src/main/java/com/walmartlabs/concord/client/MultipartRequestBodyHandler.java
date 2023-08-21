package com.walmartlabs.concord.client;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public final class MultipartRequestBodyHandler {

    public static HttpEntity handle(ObjectMapper objectMapper, Map<String, Object> data) {
        MultipartEntityBuilder multiPartBuilder = MultipartEntityBuilder.create();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof InputStream) {
                multiPartBuilder.addBinaryBody(k, (InputStream) v);
            } else if (v instanceof byte[]) {
                multiPartBuilder.addBinaryBody(k, (byte[]) v);
            } else if (v instanceof String) {
                multiPartBuilder.addTextBody(k, (String) v);
            } else if (v instanceof Path) {
                multiPartBuilder.addBinaryBody(k, ((Path) v).toFile());
            } else if (v instanceof Map) {
                String json;
                try {
                    json = objectMapper.writeValueAsString(v);
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
                multiPartBuilder.addTextBody(k, json, ContentType.APPLICATION_JSON);
            } else if (v instanceof Boolean) {
                multiPartBuilder.addTextBody(k, v.toString());
            } else if (v instanceof String[]) {
                multiPartBuilder.addTextBody(k, String.join(",", (String[]) v));
            } else if (v instanceof UUID) {
                multiPartBuilder.addTextBody(k, v.toString());
            } else if (v instanceof Enum<?>) {
                multiPartBuilder.addTextBody(k, ((Enum<?>)v).name());
            } else {
                throw new IllegalArgumentException("Unknown input type: " + k + "=" + v + (v != null ? " (" + v.getClass() + ")" : ""));
            }
        }

        return multiPartBuilder.build();
    }

    private MultipartRequestBodyHandler() {
    }
}
