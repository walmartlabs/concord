package com.walmartlabs.concord.server.org.project;

import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.SecureRandom;
import java.util.UUID;

@Named
public class EncryptedProjectValueManager {

    private static final int PROJECT_SECRET_KEY_LENGTH = 128;

    private final SecretStoreConfiguration secretCfg;
    private final ProjectDao projectDao;
    private final SecureRandom secureRandom;

    @Inject
    public EncryptedProjectValueManager(SecretStoreConfiguration secretCfg, ProjectDao projectDao, SecureRandom secureRandom) {
        this.secretCfg = secretCfg;
        this.projectDao = projectDao;
        this.secureRandom = secureRandom;
    }

    /**
     * Encrypts the specified data using the project's key.
     *
     * @param projectId
     * @param data
     * @return
     */
    public byte[] encrypt(UUID projectId, byte[] data) {
        byte[] key = getDecryptedSecretKey(projectId);
        return SecretUtils.encrypt(data, key, secretCfg.getProjectSecretsSalt());
    }

    /**
     * Decrypts the specified data using the project's key.
     *
     * @param projectId
     * @param data
     * @return
     */
    public byte[] decrypt(UUID projectId, byte[] data) {
        byte[] key = getDecryptedSecretKey(projectId);
        return SecretUtils.decrypt(data, key, secretCfg.getProjectSecretsSalt());
    }

    private byte[] getDecryptedSecretKey(UUID projectId) {
        byte[] ab = projectDao.getOrUpdateSecretKey(projectId, () -> {
            // use the old name-based key for the existing projects
            // new projects must be created with a more secure key
            String name = projectDao.getName(projectId);
            byte[] key = name.getBytes();
            return SecretUtils.encrypt(key, null, secretCfg.getSecretStoreSalt());
        });

        return SecretUtils.decrypt(ab, null, secretCfg.getSecretStoreSalt());
    }

    public byte[] createEncryptedSecretKey() {
        byte[] key = new byte[PROJECT_SECRET_KEY_LENGTH];
        secureRandom.nextBytes(key);
        return SecretUtils.encrypt(key, null, secretCfg.getSecretStoreSalt());
    }
}
