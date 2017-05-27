package com.walmartlabs.concord.server.security.secret;

import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.security.PasswordManager;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;

import javax.inject.Inject;
import javax.inject.Named;

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

    public Secret getSecret(String name) {
        // TODO check for permissions
        SecretDataEntry s = secretDao.get(name);
        if (s == null) {
            throw new ProcessException("Secret not found: " + name);
        }

        // TODO sessionKey
        byte[] password = passwordManager.getPassword(s.getName(), "TODO");
        byte[] salt = secretCfg.getSalt();
        return SecretUtils.decrypt(s.getType(), s.getData(), password, salt);
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
}
