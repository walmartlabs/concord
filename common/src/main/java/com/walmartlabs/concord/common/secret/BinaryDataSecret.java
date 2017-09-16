package com.walmartlabs.concord.common.secret;

public class BinaryDataSecret implements Secret {

    private final byte[] data;

    public BinaryDataSecret(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
