package com.walmartlabs.concord.client.multipart;

public class InputStreamPart implements Part {

    private final String name;
    private final String filename;
    private final byte[] content;

    public InputStreamPart(String name, String filename, byte[] content) {
        this.name = name;
        this.filename = filename;
        this.content = content;
    }

    @Override
    public byte[] getBytes(String boundary) {
        String headers = "Content-Type: application/octet-stream\r\n\r\n";
        byte[] headersBytes = headers.getBytes();
        byte[] boundaryBytes = ("\r\n--" + boundary + "\r\n").getBytes();
        byte[] partBytes = new byte[headersBytes.length + content.length + boundaryBytes.length];
        System.arraycopy(headersBytes, 0, partBytes, 0, headersBytes.length);
        System.arraycopy(content, 0, partBytes, headersBytes.length, content.length);
        System.arraycopy(boundaryBytes, 0, partBytes, headersBytes.length + content.length, boundaryBytes.length);
        return partBytes;
    }
}