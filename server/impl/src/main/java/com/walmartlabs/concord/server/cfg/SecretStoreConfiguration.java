package com.walmartlabs.concord.server.cfg;

import java.io.Serializable;
import java.nio.charset.Charset;

public class SecretStoreConfiguration implements Serializable {

    public static final Charset DEFAULT_PASSWORD_CHARSET = Charset.forName("US-ASCII");

    private final byte[] salt;

    public SecretStoreConfiguration(byte[] salt) {
        this.salt = salt;
    }

    public byte[] getSalt() {
        return salt;
    }
}
