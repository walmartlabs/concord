package com.walmartlabs.concord.server.security;

import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;

import javax.inject.Named;

@Named
public class PasswordManager {

    private static final String DUMMY_PASSWORD = "q1q1q1q1";

    public byte[] getPassword(String type, String sessionKey) {
        // TODO replace with sessionKey -> storeKey function
        return DUMMY_PASSWORD.getBytes(SecretStoreConfiguration.DEFAULT_PASSWORD_CHARSET);
    }
}
