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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MultipartBuilder {

    private static final byte[] COLONSPACE = { ':', ' ' };
    private static final byte[] CRLF = { '\r', '\n' };
    private static final byte[] DASHDASH = { '-', '-' };

    private final List<Headers> partHeaders = new ArrayList<>();
    private final List<RequestBody> partBodies = new ArrayList<>();

    private final ContentType type = ContentType.MULTIPART_FORM;
    private final String boundary;

    public MultipartBuilder() {
        this(UUID.randomUUID().toString());
    }

    public MultipartBuilder(String boundary) {
        this.boundary = boundary;
    }

    public MultipartBuilder addFormDataPart(String name, String value) {
        return addFormDataPart(name, null, RequestBody.create(null, value));
    }

    public MultipartBuilder addFormDataPart(String name, String filename, RequestBody value) {
        Objects.requireNonNull(name, "name");
        StringBuilder disposition = new StringBuilder("form-data; name=");
        appendQuotedString(disposition, name);

        if (filename != null) {
            disposition.append("; filename=");
            appendQuotedString(disposition, filename);
        }

        return addPart(Headers.of("Content-Disposition", disposition.toString()), value);
    }

    public MultipartBuilder addPart(Headers headers, RequestBody body) {
        if (body == null) {
            throw new NullPointerException("body == null");
        }
        if (headers != null && headers.get("Content-Type") != null) {
            throw new IllegalArgumentException("Unexpected header: Content-Type");
        }
        if (headers != null && headers.get("Content-Length") != null) {
            throw new IllegalArgumentException("Unexpected header: Content-Length");
        }

        partHeaders.add(headers);
        partBodies.add(body);
        return this;
    }

    public RequestBody build() {
//        if (partHeaders.isEmpty()) {
//            throw new IllegalStateException("Multipart body must have at least one part.");
//        }
        return new MultipartRequestBody(type, boundary, partHeaders, partBodies);
    }

    private static void appendQuotedString(StringBuilder target, String key) {
        target.append('"');
        for (int i = 0, len = key.length(); i < len; i++) {
            char ch = key.charAt(i);
            switch (ch) {
                case '\n':
                    target.append("%0A");
                    break;
                case '\r':
                    target.append("%0D");
                    break;
                case '"':
                    target.append("%22");
                    break;
                default:
                    target.append(ch);
                    break;
            }
        }
        target.append('"');
    }

    private static final class MultipartRequestBody extends RequestBody {
        private final String boundary;
        private final ContentType contentType;
        private final List<Headers> partHeaders;
        private final List<RequestBody> partBodies;

        public MultipartRequestBody(ContentType type, String boundary, List<Headers> partHeaders,
                                    List<RequestBody> partBodies) {

            Objects.requireNonNull(type, "type");

            this.boundary = boundary;
            this.contentType = type.withParameters(Collections.singletonList(new NameValuePair("boundary", boundary)));
            this.partHeaders = partHeaders;
            this.partBodies = partBodies;
        }

        @Override
        public ContentType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() throws IOException {
            return -1;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            ByteArrayBuffer boundaryEncoded = encode(StandardCharsets.US_ASCII, this.boundary);

            for (int p = 0, partCount = partHeaders.size(); p < partCount; p++) {
                Headers headers = partHeaders.get(p);
                RequestBody body = partBodies.get(p);

                out.write(DASHDASH);
                write(boundaryEncoded, out);
                out.write(CRLF);

                if (headers != null) {
                    for (int h = 0, headerCount = headers.size(); h < headerCount; h++) {
                        writeHeader(headers.name(h), headers.value(h), out);
                    }
                }

                ContentType contentType = body.contentType();
                if (contentType != null) {
                    writeHeader("Content-Type", contentType.toString(), out);
                }

                long contentLength = body.contentLength();
                if (contentLength != -1) {
                    writeHeader("Content-Length", String.valueOf(contentLength), out);
                }

                out.write(CRLF);

                body.writeTo(out);

                out.write(CRLF);
            }

            out.write(DASHDASH);
            write(boundaryEncoded, out);
            out.write(DASHDASH);
            out.write(CRLF);
        }

        private void writeHeader(String name, String value, OutputStream out) throws IOException {
            write(encodeHeader(name), out);
            out.write(COLONSPACE);
            write(encodeHeader(value), out);
            out.write(CRLF);
        }

        private static ByteArrayBuffer encode(Charset charset, String string) {
            ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
            ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
            bab.append(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
            return bab;
        }

        private static ByteArrayBuffer encodeHeader(String value) {
            return encode(StandardCharsets.ISO_8859_1, value);
        }

        private static void write(ByteArrayBuffer buff, OutputStream out) throws IOException {
            out.write(buff.array(), 0, buff.length());
        }
    }
}
