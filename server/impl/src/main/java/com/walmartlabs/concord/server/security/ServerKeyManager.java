package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;

import javax.inject.Named;

@Named
public class ServerKeyManager {

    private static final String DUMMY_PASSWORD = "q1q1q1q1";

    public byte[] getKey() {
        return DUMMY_PASSWORD.getBytes(SecretStoreConfiguration.DEFAULT_PASSWORD_CHARSET);
    }
}
