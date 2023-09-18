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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ContentType {

    public static final ContentType APPLICATION_JSON = create(
            "application/json", StandardCharsets.UTF_8);

    public static final ContentType APPLICATION_OCTET_STREAM = create(
            "application/octet-stream");

    public static final ContentType TEXT_PLAIN = create("text/plain");

    public static final ContentType MULTIPART_FORM = create("multipart/form-data");

    public static ContentType create(String mimeType) {
        return create(mimeType, null);
    }

    public static ContentType create(String mimeType, Charset charset) {
        String normalizedMimeType = mimeType.toLowerCase();
        return new ContentType(normalizedMimeType, charset);
    }

    private final String mimeType;
    private final Charset charset;
    private final List<NameValuePair> params;

    public ContentType(String mimeType, Charset charset) {
        this(mimeType, charset, null);
    }

    public ContentType(String mimeType, Charset charset, List<NameValuePair> params) {
        this.mimeType = mimeType;
        this.charset = charset;
        this.params = params;
    }

    public ContentType withCharset(Charset charset) {
        return create(getMimeType(), charset);
    }

    public ContentType withParameters(List<NameValuePair> params) {
        return new ContentType(getMimeType(), charset, params);
    }

    public String getMimeType() {
        return mimeType;
    }

    public Charset getCharset() {
        return charset;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.mimeType);
        if (this.params != null) {
            buf.append("; ");
            formatParameters(buf, this.params);
        } else if (this.charset != null) {
            buf.append("; charset=");
            buf.append(this.charset.name().toLowerCase());
        }
        return buf.toString();
    }

    private static void formatParameters(StringBuilder buf, List<NameValuePair> params) {
        String s = params.stream()
                .map(nvp -> nvp.getName() + "=" + nvp.getValue())
                .collect(Collectors.joining("; "));

        buf.append(s);
    }
}
