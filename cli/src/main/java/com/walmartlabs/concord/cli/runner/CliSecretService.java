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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CliSecretService implements SecretService {

    private final Path workDir;
    private final Path secretStoreDir;
    private final VaultProvider vaultProvider;

    private final ObjectMapper om = new ObjectMapper();

    public CliSecretService(Path workDir, Path secretStoreDir, VaultProvider vaultProvider) {
        this.workDir = workDir;
        this.secretStoreDir = secretStoreDir;
        this.vaultProvider = vaultProvider;
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        Path publicKey = toSecretPath(orgName, secretName + ".pub");
        Path privateKey = toSecretPath(orgName, secretName);

        if (Files.notExists(publicKey)) {
            throw new RuntimeException("Public key '" + publicKey + "' not found");
        }

        if (Files.notExists(privateKey)) {
            throw new RuntimeException("Private key '" + privateKey + "' not found");
        }

        Path tmpDir = assertTmpDir(workDir);
        Path tmpPublicKey = tmpDir.resolve(secretName + ".pub");
        Path tmpPrivateKey = tmpDir.resolve(secretName);
        Files.copy(publicKey, tmpPublicKey, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(privateKey, tmpPrivateKey, StandardCopyOption.REPLACE_EXISTING);

        return SecretService.KeyPair.builder()
                .privateKey(tmpPrivateKey)
                .publicKey(tmpPublicKey)
                .build();
    }

    @Override
    public String exportAsString(String orgName, String secretName, String password) throws IOException {
        Path secretPath = assertSecret(orgName, secretName);
        return new String(Files.readAllBytes(secretPath)).trim();
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password) throws IOException {
        Path secretPath = assertSecret(orgName, secretName);

        Path tmpDir = assertTmpDir(workDir);
        Path dest = Files.createTempFile(tmpDir, "file", ".bin");
        Files.copy(secretPath, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    @Override
    public String decryptString(String encryptedValue) {
        return vaultProvider.getValue(encryptedValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public UsernamePassword exportCredentials(String orgName, String secretName, String password) {
        Path secretPath = assertSecret(orgName, secretName);

        try {
            Map<String, String> up = om.readValue(secretPath.toFile(), Map.class);
            return UsernamePassword.of(up.get("username"), up.get("password"));
        } catch (IOException e) {
            throw new RuntimeException("Invalid secret '" + orgName + "/" + secretName + "' ('" + secretPath + "') format: " + e.getMessage());
        }
    }

    @Override
    public String encryptString(String orgName, String projectName, String value) {
        throw new UnsupportedOperationException("Encrypting secrets is not supported by concord-cli yet");
    }

    @Override
    public SecretCreationResult createKeyPair(SecretParams secret, KeyPair keyPair) throws Exception {
        Path publicKey = createSecretFile(secret.orgName(), secret.secretName() + ".pub");
        Path privateKey = createSecretFile(secret.orgName(), secret.secretName());

        Files.copy(keyPair.publicKey(), publicKey);
        Files.copy(keyPair.privateKey(), privateKey);

        return SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Override
    public SecretCreationResult createUsernamePassword(SecretParams secret, UsernamePassword usernamePassword) throws Exception {
        Path path = createSecretFile(secret.orgName(), secret.secretName());

        Map<String, Object> creds = new HashMap<>();
        creds.put("username", usernamePassword.username());
        creds.put("password", usernamePassword.password());

        om.writeValue(path.toFile(), creds);

        return SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Override
    public SecretCreationResult createData(SecretParams secret, byte[] data) throws Exception {
        Path path = createSecretFile(secret.orgName(), secret.secretName());

        Files.write(path, data);

        return SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    private Path assertSecret(String orgName, String secretName) {
        Path secretPath = toSecretPath(orgName, secretName);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '" + secretPath + "' not found");
        }
        return secretPath;
    }

    private Path createSecretFile(String orgName, String secretName) throws IOException {
        Path path = toSecretPath(orgName, secretName);

        if (Files.exists(path)) {
            throw new RuntimeException("Secret '" + orgName + "/" + secretName + "' ('" + path + "') already exists");
        }

        if (Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        return path;
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
