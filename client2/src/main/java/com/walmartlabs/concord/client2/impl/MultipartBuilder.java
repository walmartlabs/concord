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
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MultipartBuilder {

    private static final byte[] COLONSPACE = {':', ' '};
    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] DASHDASH = {'-', '-'};

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
        public long contentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() throws IOException {
            SequenceInputStreamBuilder result = new SequenceInputStreamBuilder();
            try {
                write(result);
                return result.build();
            } catch (Exception e) {
                result.close();
                throw e;
            }
        }

        private void write(SequenceInputStreamBuilder result) throws IOException {
            ByteArrayBuffer boundaryEncoded = encode(StandardCharsets.US_ASCII, this.boundary);

            for (int p = 0, partCount = partHeaders.size(); p < partCount; p++) {
                Headers headers = partHeaders.get(p);
                RequestBody body = partBodies.get(p);

                result.write(DASHDASH);
                result.write(boundaryEncoded);
                result.write(CRLF);

                if (headers != null) {
                    for (int h = 0, headerCount = headers.size(); h < headerCount; h++) {
                        writeHeader(headers.name(h), headers.value(h), result);
                    }
                }

                ContentType contentType = body.contentType();
                if (contentType != null) {
                    writeHeader("Content-Type", contentType.toString(), result);
                }

                long contentLength = body.contentLength();
                if (contentLength != -1) {
                    writeHeader("Content-Length", String.valueOf(contentLength), result);
                }

                result.write(CRLF);
                result.write(body.getContent());
                result.write(CRLF);
            }

            result.write(DASHDASH);
            result.write(boundaryEncoded);
            result.write(DASHDASH);
            result.write(CRLF);
        }

        private void writeHeader(String name, String value, SequenceInputStreamBuilder out) throws IOException {
            out.write(encodeHeader(name));
            out.write(COLONSPACE);
            out.write(encodeHeader(value));
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
    }

    static class SequenceInputStreamBuilder {

        private final Vector<InputStream> streams = new Vector<>();
        private final ByteArrayBuffer currentBuffer = new ByteArrayBuffer(1024);

        public void write(byte[] buff) {
            currentBuffer.append(buff);
        }

        public void write(ByteArrayBuffer buff) {
            currentBuffer.append(buff.array(), 0, buff.length());
        }

        public void write(InputStream stream) {
            flushCurrentBuffer();

            streams.add(stream);
        }

        public void close() throws IOException {
            IOException ioe = null;
            for (InputStream in : streams) {
                try {
                    in.close();
                } catch (IOException e) {
                    if (ioe == null) {
                        ioe = e;
                    } else {
                        ioe.addSuppressed(e);
                    }
                }
            }
            if (ioe != null) {
                throw ioe;
            }
        }

        public InputStream build() {
            flushCurrentBuffer();

            return new SequenceInputStream(streams.elements());
        }

        private void flushCurrentBuffer() {
            if (currentBuffer.length() > 0) {
                streams.add(new ByteArrayInputStream(currentBuffer.toByteArray(), 0, currentBuffer.length()));
                currentBuffer.clear();
            }
        }
    }
}
