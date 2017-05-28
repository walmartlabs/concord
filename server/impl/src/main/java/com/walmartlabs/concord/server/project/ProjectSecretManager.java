package com.walmartlabs.concord.server.project;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.secret.BinaryDataSecret;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import com.walmartlabs.concord.server.security.secret.SecretUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Named
public class ProjectSecretManager {

    private static final int DEFAULT_KEY_LENGTH = 16;

    private final SecureRandom rng;
    private final SecretManager secretManager;
    private final SecretStoreConfiguration secretCfg;

    @Inject
    public ProjectSecretManager(SecureRandom rng, SecretManager secretManager, SecretStoreConfiguration secretCfg) {
        this.rng = rng;
        this.secretManager = secretManager;
        this.secretCfg = secretCfg;
    }

    public byte[] encrypt(String projectName, byte[] data) {
        BinaryDataSecret k = ensureSecretKey(projectName);
        try {
            return SecretUtils.encrypt(data, k.getData(), secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    public byte[] decrypt(String projectName, byte[] data) {
        String name = createSecretName(projectName);

        BinaryDataSecret k = (BinaryDataSecret) secretManager.getSecret(name);
        if (k == null) {
            return null;
        }

        try {
            return SecretUtils.decrypt(data, k.getData(), secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    // TODO #multiserver
    private synchronized BinaryDataSecret ensureSecretKey(String projectName) {
        String name = createSecretName(projectName);

        BinaryDataSecret k = (BinaryDataSecret) secretManager.getSecret(name);
        if (k == null) {
            byte[] ab = new byte[DEFAULT_KEY_LENGTH];
            rng.nextBytes(ab);

            k = new BinaryDataSecret(ab);
            secretManager.store(name, k);
        }

        return k;
    }

    private String createSecretName(String projectName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(projectName.getBytes());
            md.update(secretCfg.getProjectSecretsSalt());

            byte[] ab = md.digest(projectName.getBytes());

            String suffix = DatatypeConverter.printHexBinary(ab);
            return "project_secret_" + suffix;
        } catch (NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }
    }
}
