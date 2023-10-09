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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public abstract class RequestBody implements HttpEntity {

    public static RequestBody create(ContentType contentType, String content) {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            charset = contentType.getCharset();
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
                contentType = contentType.withCharset(charset);
            }
        }
        byte[] bytes = content.getBytes(charset);
        return create(contentType, bytes);
    }

    public static RequestBody create(ContentType contentType, byte[] content) {
        return create(contentType, content, 0, content.length);
    }

    public static RequestBody create(ContentType contentType, byte[] content, int offset, int byteCount) {
        Objects.requireNonNull(content, "content");

        return new RequestBody() {
            @Override
            public ContentType contentType() {
                return contentType;
            }

            @Override
            public long contentLength() {
                return byteCount;
            }

            @Override
            public InputStream getContent() {
                return new ByteArrayInputStream(content, offset, byteCount);
            }
        };
    }
}
