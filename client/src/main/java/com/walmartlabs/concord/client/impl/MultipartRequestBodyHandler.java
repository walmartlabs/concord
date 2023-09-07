package com.walmartlabs.concord.client.impl;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.client.impl.ContentType.*;

public final class MultipartRequestBodyHandler {

    public static HttpEntity handle(ObjectMapper objectMapper, Map<String, Object> data) {
        return handle(new MultipartBuilder(), objectMapper, data);
    }

    public static HttpEntity handle(MultipartBuilder b, ObjectMapper objectMapper, Map<String, Object> data) {
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof InputStream) {
                b.addFormDataPart(k, null, new InputStreamRequestBody((InputStream) v));
            } else if (v instanceof byte[]) {
                b.addFormDataPart(k, null, RequestBody.create(APPLICATION_OCTET_STREAM, (byte[]) v));
            } else if (v instanceof String) {
                b.addFormDataPart(k, (String) v);
            } else if (v instanceof Path) {
                b.addFormDataPart(k, null, new PathRequestBody((Path) v));
            } else if (v instanceof Map) {
                String json;
                try {
                    json = objectMapper.writeValueAsString(v);
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
                b.addFormDataPart(k, null, RequestBody.create(APPLICATION_JSON, json));
            } else if (v instanceof Boolean) {
                b.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN, v.toString()));
            } else if (v instanceof String[]) {
                b.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN, String.join(",", (String[]) v)));
            } else if (v instanceof UUID) {
                b.addFormDataPart(k, v.toString());
            } else if (v instanceof Enum<?>) {
                b.addFormDataPart(k, ((Enum<?>)v).name());
            } else {
                throw new IllegalArgumentException("Unknown input type: " + k + "=" + v + (v != null ? " (" + v.getClass() + ")" : ""));
            }
        }
        return b.build();
    }

    private MultipartRequestBodyHandler() {
    }

    public static final class InputStreamRequestBody extends RequestBody {

        private final InputStream in;

        public InputStreamRequestBody(InputStream in) {
            this.in = in;
        }

        @Override
        public ContentType contentType() {
            return APPLICATION_OCTET_STREAM;
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try {
                final byte[] tmp = new byte[4096];
                int l;
                while ((l = this.in.read(tmp)) != -1) {
                    out.write(tmp, 0, l);
                }
                out.flush();
            } finally {
                this.in.close();
            }
        }
    }

    public static class PathRequestBody extends RequestBody {

        private final Path path;

        public PathRequestBody(Path path) {
            this.path = path;
        }

        @Override
        public ContentType contentType() {
            return APPLICATION_OCTET_STREAM;
        }

        @Override
        public long contentLength() throws IOException {
            return Files.size(path);
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            try (InputStream in = Files.newInputStream(this.path)) {
                byte[] tmp = new byte[4096];
                int l;
                while ((l = in.read(tmp)) != -1) {
                    out.write(tmp, 0, l);
                }
                out.flush();
            }
        }
    }
}
