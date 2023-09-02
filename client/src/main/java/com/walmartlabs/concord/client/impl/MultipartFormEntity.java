package com.walmartlabs.concord.client.impl;

import org.apache.hc.client5.http.entity.mime.Header;
import org.apache.hc.client5.http.entity.mime.MimeField;
import org.apache.hc.client5.http.entity.mime.MultipartPart;
import org.apache.hc.core5.util.ByteArrayBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MultipartFormEntity implements HttpEntity {

    static final ByteArrayBuffer FIELD_SEP = encode(StandardCharsets.ISO_8859_1, ": ");
    static final ByteArrayBuffer CR_LF = encode(StandardCharsets.ISO_8859_1, "\r\n");
    static final ByteArrayBuffer TWO_HYPHENS = encode(StandardCharsets.ISO_8859_1, "--");

    private final List<MultipartPart> parts;
    private final String boundary;
    private final ContentType contentType;

    public MultipartFormEntity(List<MultipartPart> parts, String boundary, ContentType contentType) {
        this.parts = parts;
        this.boundary = boundary;
        this.contentType = contentType;
    }

    @Override
    public String getContentType() {
        return contentType.toString();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        doWriteTo(out, true);
    }

    void doWriteTo(OutputStream out, boolean writeContent) throws IOException {
        ByteArrayBuffer boundaryEncoded = encode(StandardCharsets.US_ASCII, this.boundary);
        for (MultipartPart part: parts) {
            writeBytes(TWO_HYPHENS, out);
            writeBytes(boundaryEncoded, out);
            writeBytes(CR_LF, out);

            formatMultipartHeader(part, out);

            writeBytes(CR_LF, out);

            if (writeContent) {
                part.getBody().writeTo(out);
            }
            writeBytes(CR_LF, out);
        }
        writeBytes(TWO_HYPHENS, out);
        writeBytes(boundaryEncoded, out);
        writeBytes(TWO_HYPHENS, out);
        writeBytes(CR_LF, out);
    }

    private static ByteArrayBuffer encode(Charset charset, String string) {
        ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
        ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
        bab.append(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
        return bab;
    }

    private static void writeBytes(ByteArrayBuffer b, OutputStream out) throws IOException {
        out.write(b.array(), 0, b.length());
    }

    static void writeBytes(String s, OutputStream out) throws IOException {
        ByteArrayBuffer b = encode(StandardCharsets.ISO_8859_1, s);
        writeBytes(b, out);
    }

    private static void formatMultipartHeader(MultipartPart part, OutputStream out) throws IOException {
        Header header = part.getHeader();
        for (MimeField field: header) {
            writeField(field, out);
        }
    }

    private static void writeField(MimeField field, OutputStream out) throws IOException {
        writeBytes(field.getName(), out);
        writeBytes(FIELD_SEP, out);
        writeBytes(field.getBody(), out);
        writeBytes(CR_LF, out);
    }

}
