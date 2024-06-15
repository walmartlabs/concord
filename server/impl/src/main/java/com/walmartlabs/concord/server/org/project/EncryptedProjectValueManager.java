package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.UUID;

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
        try {
            byte[] key = getDecryptedSecretKey(projectId);
            return SecretUtils.decrypt(data, key, secretCfg.getProjectSecretsSalt());
        } catch (SecurityException e) {
            String message = e.getMessage();

            Throwable cause = e.getCause();
            if (cause instanceof IllegalBlockSizeException) {
                message = "Invalid encrypted value, please verify that the value is a correct encrypted string";
            } else if (cause instanceof BadPaddingException) {
                message = "Please verify that the value is correct and is encrypted using the correct project";
            }

            throw new SecurityException("Decrypt error: " + message);
        }
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
