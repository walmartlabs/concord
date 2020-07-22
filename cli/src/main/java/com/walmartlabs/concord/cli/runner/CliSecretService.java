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

import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CliSecretService {

    private final Path secretStoreDir;
    private final VaultProvider vaultProvider;

    public CliSecretService(Path secretStoreDir, VaultProvider vaultProvider) {
        this.secretStoreDir = secretStoreDir;
        this.vaultProvider = vaultProvider;
    }

    public SecretService.KeyPair exportKeyAsFile(Path workDir, String orgName, String name) throws Exception {
        Path publicKey = toSecretPath(orgName, name + ".pub");
        Path privateKey = toSecretPath(orgName, name);

        if (Files.notExists(publicKey)) {
            throw new RuntimeException("Public key '" + publicKey + "' not found");
        }

        if (Files.notExists(privateKey)) {
            throw new RuntimeException("Private key '" + privateKey + "' not found");
        }

        Path tmpDir = assertTmpDir(workDir);
        Path tmpPublicKey = tmpDir.resolve(name + ".pub");
        Path tmpPrivateKey = tmpDir.resolve(name);
        Files.copy(publicKey, tmpPublicKey, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(privateKey, tmpPrivateKey, StandardCopyOption.REPLACE_EXISTING);

        return SecretService.KeyPair.builder()
                .privateKey(tmpPrivateKey)
                .publicKey(tmpPublicKey)
                .build();
    }

    public String decryptString(String encryptedString) {
        return vaultProvider.getValue(encryptedString);
    }

    public String exportAsString(String orgName, String name) throws IOException {
        Path secretPath = toSecretPath(orgName, name);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '" + secretPath + "' not found");
        }
        return new String(Files.readAllBytes(secretPath)).trim();
    }

    public Path exportAsFile(Path workDir, String orgName, String name) throws IOException {
        Path secretPath = toSecretPath(orgName, name);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '" + secretPath + "' not found");
        }

        Path tmpDir = assertTmpDir(workDir);
        Path dest = Files.createTempFile(tmpDir, "file", ".bin");
        Files.copy(secretPath, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private Path toSecretPath(String orgName, String name) {
        Path secretPath = secretStoreDir;
        if (orgName != null) {
            secretPath = secretStoreDir.resolve(orgName);
        }

        return secretPath.resolve(name);
    }

    private static Path assertTmpDir(Path workDir) throws IOException {
        Path dir = workDir.resolve("target").resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (Files.notExists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }
}
