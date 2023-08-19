package com.walmartlabs.concord.client.multipart;

public class TextPart implements Part {
    private final String name;
    private final String value;

    public TextPart(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public byte[] getBytes(String boundary) {
        String headers = "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
        return (headers + value + "\r\n").getBytes();
    }
}