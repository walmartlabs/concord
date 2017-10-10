package com.walmartlabs.concord.server.security.secret;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.secret.*;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.ServerKeyManager;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.function.Function;

@Named
public class SecretManager {

    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;
    private final ServerKeyManager serverKeyManager;

    @Inject
    public SecretManager(SecretDao secretDao,
                         SecretStoreConfiguration secretCfg,
                         ServerKeyManager serverKeyManager) {

        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
        this.serverKeyManager = serverKeyManager;
    }

    public KeyPair createKeyPair(String name, String password) {
        KeyPair k = KeyPairUtils.create();
        store(name, k, password);
        return k;
    }

    public Secret getSecret(String name, String password) {
        SecretDataEntry s = secretDao.getByName(name);
        if (s == null) {
            return null;
        }

        SecretStoreType providedStoreType = getStoreType(password);
        assertStoreType(name, providedStoreType, s.getStoreType());

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        return decrypt(s.getType(), s.getData(), pwd, salt);
    }

    public SecretDataEntry getRaw(String name, String password) {
        SecretDataEntry s = secretDao.getByName(name);
        if (s == null) {
            return null;
        }

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        try {
            byte[] ab = SecretUtils.decrypt(s.getData(), pwd, salt);
            return new SecretDataEntry(s, ab);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error decrypting a secret: " + name);
        }
    }

    public KeyPair getKeyPair(String name, String password) {
        Secret s = getSecret(name, password);
        if (s == null) {
            return null;
        }

        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Invalid secret type: " + name + ", expected a key pair, got: " + s.getClass());
        }

        return (KeyPair) s;
    }

    public void store(String name, Secret s, String password) {
        byte[] data;

        SecretType type;
        if (s instanceof KeyPair) {
            type = SecretType.KEY_PAIR;
            data = KeyPair.serialize((KeyPair) s);
        } else if (s instanceof UsernamePassword) {
            type = SecretType.USERNAME_PASSWORD;
            data = UsernamePassword.serialize((UsernamePassword) s);
        } else if (s instanceof BinaryDataSecret) {
            type = SecretType.DATA;
            data = ((BinaryDataSecret) s).getData();
        } else {
            throw new IllegalArgumentException("Unknown secret type: " + s.getClass());
        }

        SecretStoreType storeType = getStoreType(password);

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        byte[] ab;
        try {
            ab = SecretUtils.encrypt(data, pwd, salt);
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }

        secretDao.insert(name, type, storeType, ab);
    }

    public byte[] encryptData(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.encrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    public byte[] decryptData(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.decrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    private static void assertStoreType(String name, SecretStoreType provided, SecretStoreType actual) {
        if (provided == actual) {
            return;
        }

        switch (actual) {
            case SERVER_KEY:
                throw new SecurityException("Not a password-protected secret: " + name);
            case PASSWORD:
                throw new SecurityException("The secret requires a password to decrypt: " + name);
            default:
                throw new IllegalArgumentException("Unsupported secret store type: " + actual);
        }
    }

    private static Secret decrypt(SecretType type, byte[] input, byte[] password, byte[] salt) {
        Function<byte[], ? extends Secret> deserializer;
        switch (type) {
            case KEY_PAIR:
                deserializer = KeyPair::deserialize;
                break;
            case USERNAME_PASSWORD:
                deserializer = UsernamePassword::deserialize;
                break;
            case DATA:
                deserializer = BinaryDataSecret::new;
                break;
            default:
                throw new IllegalArgumentException("Unknown secret type: " + type);
        }

        return SecretUtils.decrypt(deserializer, input, password, salt);
    }

    private byte[] getPwd(String pwd) {
        if (pwd == null) {
            return serverKeyManager.getKey();
        }
        return pwd.getBytes(StandardCharsets.UTF_8);
    }

    private SecretStoreType getStoreType(String pwd) {
        if (pwd == null) {
            return SecretStoreType.SERVER_KEY;
        }
        return SecretStoreType.PASSWORD;
    }
}
