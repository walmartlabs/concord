package com.walmartlabs.concord.server.api.org.secret;

public enum SecretType {

    /**
     * A SSH key pair.
     */
    KEY_PAIR,

    /**
     * An username and a password.
     */
    USERNAME_PASSWORD,

    /**
     * Binary data.
     */
    DATA
}
