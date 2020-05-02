package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.cli.VaultProvider;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CliSecretService {

    private final Path secretStoreDir;
    private final VaultProvider vaultProvider;

    public CliSecretService(Path secretStoreDir, VaultProvider vaultProvider) {
        this.secretStoreDir = secretStoreDir;
        this.vaultProvider = vaultProvider;
    }

    public SecretService.KeyPair exportKeyAsFile(Path workDir, String orgName, String name, String password) throws Exception {
        Path publicKey = secretStoreDir;
        Path privateKey = secretStoreDir;
        if (orgName != null) {
            publicKey = publicKey.resolve(orgName);
            privateKey = privateKey.resolve(orgName);
        }
        publicKey = publicKey.resolve(name + ".pub");
        privateKey = privateKey.resolve(name);

        if (Files.notExists(publicKey)) {
            throw new RuntimeException("Public key '" + publicKey + "' not found");
        }

        if (Files.notExists(privateKey)) {
            throw new RuntimeException("Private key '" + privateKey + "' not found");
        }

        Path dest = workDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        Path tmpPublicKey = dest.resolve(name + ".pub");
        Path tmpPrivateKey = dest.resolve(name);
        Files.copy(publicKey, tmpPublicKey);
        Files.copy(privateKey, tmpPrivateKey);

        return SecretService.KeyPair.builder()
                .privateKey(tmpPrivateKey)
                .publicKey(tmpPublicKey)
                .build();
    }

    public String decryptString(String encryptedString) {
        return vaultProvider.getValue(encryptedString);
    }

    public String exportAsString(String orgName, String name, String password) throws IOException {
        Path secretPath = secretStoreDir;
        if (orgName != null) {
            secretPath = secretStoreDir.resolve(orgName);
        }

        secretPath = secretPath.resolve(name);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '" + secretPath + "' not found");
        }
        return new String(Files.readAllBytes(secretPath));
    }
}
