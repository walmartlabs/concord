package com.walmartlabs.concord.server.project;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.secret.SecretUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.GeneralSecurityException;

@Named
public class ProjectSecretManager {

    private final SecretStoreConfiguration secretCfg;

    @Inject
    public ProjectSecretManager(SecretStoreConfiguration secretCfg) {
        this.secretCfg = secretCfg;
    }

    public byte[] encrypt(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.encrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    public byte[] decrypt(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.decrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }
}
