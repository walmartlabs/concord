package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.nio.charset.Charset;

@Named
@Singleton
public class SecretStoreConfiguration implements Serializable {

    public static final Charset DEFAULT_PASSWORD_CHARSET = Charset.forName("US-ASCII");

    public static final String SERVER_PASSWORD_KEY = "SERVER_PASSWORD";
    public static final String SECRET_STORE_SALT_KEY = "SECRET_STORE_SALT";
    public static final String PROJECT_SECRETS_SALT_KEY = "PROJECT_SECRETS_SALT";

    private static final byte[] DEFAULT_SERVER_PASSWORD = "q1q1q1q1".getBytes(DEFAULT_PASSWORD_CHARSET);
    // obligatory https://xkcd.com/221/
    private static final byte[] DEFAULT_SECRET_STORE_SALT = {0x48, 0x29, 0x38, 0x2a, 0x60, 0x65, 0x6b, 0x33, 0x22};
    private static final byte[] DEFAULT_PROJECT_SECRETS_SALT = {0x23, 0x7e, 0x31, 0x0a, 0x67, 0x0e, 0x0b, 0x05, 0x6f};

    private final byte[] serverPwd;
    private final byte[] salt;
    private final byte[] projectSecretsSalt;

    public SecretStoreConfiguration() {
        this.serverPwd = get(SERVER_PASSWORD_KEY, DEFAULT_SERVER_PASSWORD);
        this.salt = get(SECRET_STORE_SALT_KEY, DEFAULT_SECRET_STORE_SALT);
        this.projectSecretsSalt = get(PROJECT_SECRETS_SALT_KEY, DEFAULT_PROJECT_SECRETS_SALT);
    }

    public byte[] getServerPwd() {
        return serverPwd;
    }

    public byte[] getSecretStoreSalt() {
        return salt;
    }

    public byte[] getProjectSecretsSalt() {
        return projectSecretsSalt;
    }

    private static byte[] get(String key, byte[] defaultSalt) {
        byte[] ab = defaultSalt;
        String s = System.getenv(key);
        if (s != null) {
            ab = s.getBytes(DEFAULT_PASSWORD_CHARSET);
        }
        return ab;
    }
}
