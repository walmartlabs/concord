package com.walmartlabs.concord.server.security.secret;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.PasswordManager;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.GeneralSecurityException;
import java.util.function.Function;

@Named
public class SecretManager {

    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;
    private final PasswordManager passwordManager;

    @Inject
    public SecretManager(SecretDao secretDao, SecretStoreConfiguration secretCfg, PasswordManager passwordManager) {
        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
        this.passwordManager = passwordManager;
    }

    public KeyPair createKeypair(String name, String sessionKey) {
        byte[] password = passwordManager.getPassword(name, sessionKey);

        KeyPair k = KeyPair.create();
        byte[] ab = SecretUtils.encrypt(KeyPair::serialize, k, password, secretCfg.getSecretStoreSalt());

        secretDao.insert(name, SecretType.KEY_PAIR, ab);
        return k;
    }

    public Secret getSecret(String name) {
        // TODO check for permissions?
        SecretDataEntry s = secretDao.get(name);
        if (s == null) {
            return null;
        }

        // TODO sessionKey
        byte[] password = passwordManager.getPassword(s.getName(), "TODO");
        byte[] salt = secretCfg.getSecretStoreSalt();
        return decrypt(s.getType(), s.getData(), password, salt);
    }

    public KeyPair getKeyPair(String name) {
        Secret s = getSecret(name);
        if (s == null) {
            return null;
        }

        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Invalid secret type: " + name + ", expected a key pair, got: " + s.getClass());
        }
        return (KeyPair) s;
    }

    public void store(String name, Secret s) {
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

        byte[] password = passwordManager.getPassword(name, "TODO");
        byte[] salt = secretCfg.getSecretStoreSalt();

        byte[] ab;
        try {
            ab = SecretUtils.encrypt(data, password, salt);
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }

        secretDao.insert(name, type, ab);
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
                deserializer = (data) -> new BinaryDataSecret(data);
                break;
            default:
                throw new IllegalArgumentException("Unknown secret type: " + type);
        }

        return SecretUtils.decrypt(deserializer, input, password, salt);
    }
}
