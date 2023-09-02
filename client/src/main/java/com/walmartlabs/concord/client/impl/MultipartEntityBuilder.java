package com.walmartlabs.concord.client.impl;

import org.apache.hc.client5.http.entity.mime.*;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Asserts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MultipartFormEntityBuilder {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String FIELD_PARAM_NAME = "name";
    public static final String FIELD_PARAM_FILENAME = "filename";

    private List<MultipartPart> multipartParts;

    public MultipartFormEntityBuilder addBinaryBody(String name, InputStream stream) {
        return addBinaryBody(name, stream, ContentType.APPLICATION_OCTET_STREAM, null);
    }

    public MultipartFormEntityBuilder addBinaryBody(
            String name, InputStream stream, ContentType contentType, String filename) {
        return addPart(name, new InputStreamBody(stream, contentType, filename));
    }

    public MultipartFormEntityBuilder addTextBody(String name, String text) {
        return addTextBody(name, text, ContentType.TEXT_PLAIN);
    }

    public MultipartFormEntityBuilder addTextBody(
            String name, String text, ContentType contentType) {
        return addPart(name, new StringBody(text, contentType));
    }

    private MultipartFormEntityBuilder addPart(String name, ContentBody contentBody) {
        Objects.requireNonNull(name, "Name");
        Objects.requireNonNull(contentBody, "Content body");
        return addPart(buildPart(name, contentBody));
    }

    public MultipartFormEntityBuilder addPart(MultipartPart multipartPart) {
        if (multipartPart == null) {
            return this;
        }
        if (this.multipartParts == null) {
            this.multipartParts = new ArrayList<>();
        }
        this.multipartParts.add(multipartPart);
        return this;
    }

    private MultipartFormEntity buildEntity() {
        String boundary = UUID.randomUUID().toString();

        ContentType contentType = ContentType.MULTIPART_FORM;

        List<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair("boundary", boundary));

        contentType = contentType.withParameters(params);

        return new MultipartFormEntity(multipartParts, boundary, contentType);
    }

    public HttpEntity build() {
        return buildEntity();
    }

    private static FormBodyPart buildPart(String name, ContentBody contentBody) {
        ContentType contentType = contentBody.getContentType();


        Header headerCopy = new Header();
        // content type
        headerCopy.addField(new MimeField(CONTENT_TYPE, contentType.toString()));

        // content disposition
        List<NameValuePair> fieldParameters = new ArrayList<>();
        fieldParameters.add(new NameValuePair(FIELD_PARAM_NAME, name));
        if (contentBody.getFilename() != null) {
            fieldParameters.add(new NameValuePair(FIELD_PARAM_FILENAME, contentBody.getFilename()));
        }
        headerCopy.addField(new MimeField(CONTENT_DISPOSITION, "form-data", fieldParameters));

        return new FormBodyPart(name, contentBody, headerCopy);
    }

    class InputStreamBody implements ContentBody {

        private final InputStream in;
        private final ContentType contentType;
        private final String filename;

        public InputStreamBody(InputStream in, ContentType contentType, String filename) {
            this.in = in;
            this.contentType = contentType;
            this.filename = filename;
        }

        @Override
        public ContentType getContentType() {
            return contentType;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public void writeTo(final OutputStream out) throws IOException {
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

    class StringBody implements ContentBody {

        private final ContentType contentType;
        private final byte[] content;

        public StringBody(String text,ContentType contentType) {
            this.contentType = contentType;
            final Charset charset = contentType.getCharset();
            this.content = text.getBytes(charset != null ? charset : StandardCharsets.US_ASCII);
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            final InputStream in = new ByteArrayInputStream(this.content);
            final byte[] tmp = new byte[4096];
            int l;
            while ((l = in.read(tmp)) != -1) {
                out.write(tmp, 0, l);
            }
            out.flush();
        }

        @Override
        public String getFilename() {
            return null;
        }
    }

    interface ContentBody {

        ContentType getContentType();

        String getFilename();

        void writeTo(OutputStream out) throws IOException;
    }
}
