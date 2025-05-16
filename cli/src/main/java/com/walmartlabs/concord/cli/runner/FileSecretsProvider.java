package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple file-based secret provider.
 * Secrets stored unencrypted in ${secretStoreDir}/${orgName}/${secretName}.
 */
public class FileSecretsProvider implements SecretsProvider {

    private static final TypeReference<Map<String, String>> MAP_OF_STRINGS = new TypeReference<>() {
    };

    private final Path workDir;
    private final Path secretStoreDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileSecretsProvider(Path workDir, Path secretStoreDir) {
        this.workDir = workDir;
        this.secretStoreDir = secretStoreDir;
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        var publicKey = toSecretPath(orgName, secretName + ".pub");
        var privateKey = toSecretPath(orgName, secretName);

        if (Files.notExists(publicKey)) {
            throw new RuntimeException("Public key '" + publicKey + "' not found");
        }

        if (Files.notExists(privateKey)) {
            throw new RuntimeException("Private key '" + privateKey + "' not found");
        }

        var tmpDir = assertTmpDir(workDir);
        var tmpPublicKey = tmpDir.resolve(secretName + ".pub");
        var tmpPrivateKey = tmpDir.resolve(secretName);
        Files.copy(publicKey, tmpPublicKey, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(privateKey, tmpPrivateKey, StandardCopyOption.REPLACE_EXISTING);

        return SecretService.KeyPair.builder()
                .privateKey(tmpPrivateKey)
                .publicKey(tmpPublicKey)
                .build();
    }

    @Override
    public String exportAsString(String orgName, String secretName, String password) throws IOException {
        var secretPath = assertSecret(orgName, secretName);
        return new String(Files.readAllBytes(secretPath)).trim();
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password) throws IOException {
        var secretPath = assertSecret(orgName, secretName);

        var tmpDir = assertTmpDir(workDir);
        var dest = Files.createTempFile(tmpDir, "file", ".bin");
        Files.copy(secretPath, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    @Override
    public SecretService.UsernamePassword exportCredentials(String orgName, String secretName, String secretPassword) {
        var secretPath = assertSecret(orgName, secretName);

        try {
            var data = objectMapper.readTree(secretPath.toFile());

            var username = Optional.ofNullable(data.get("username")).map(JsonNode::asText)
                    .orElseThrow(() -> new IllegalStateException("Secret %s/%s is missing the username field".formatted(orgName, secretName)));
            var password = Optional.ofNullable(data.get("password")).map(JsonNode::asText)
                    .orElseThrow(() -> new IllegalStateException("Secret %s/%s is missing the password field".formatted(orgName, secretName)));

            return SecretService.UsernamePassword.of(username, password);
        } catch (IOException e) {
            throw new RuntimeException("Invalid secret '" + orgName + "/" + secretName + "' ('" + secretPath + "') format: " + e.getMessage());
        }
    }

    @Override
    public SecretService.SecretCreationResult createKeyPair(SecretService.SecretParams secret, SecretService.KeyPair keyPair) throws Exception {
        var publicKey = createSecretFile(secret.orgName(), secret.secretName() + ".pub");
        var privateKey = createSecretFile(secret.orgName(), secret.secretName());

        Files.copy(keyPair.publicKey(), publicKey);
        Files.copy(keyPair.privateKey(), privateKey);

        return SecretService.SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Override
    public SecretService.SecretCreationResult createUsernamePassword(SecretService.SecretParams secret, SecretService.UsernamePassword usernamePassword) throws Exception {
        var path = createSecretFile(secret.orgName(), secret.secretName());

        objectMapper.writeValue(path.toFile(), Map.of(
                "username", usernamePassword.username(),
                "password", usernamePassword.password()
        ));

        return SecretService.SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    @Override
    public SecretService.SecretCreationResult createData(SecretService.SecretParams secret, byte[] data) throws Exception {
        var path = createSecretFile(secret.orgName(), secret.secretName());

        Files.write(path, data);

        return SecretService.SecretCreationResult.builder()
                .id(UUID.randomUUID())
                .build();
    }

    private Path assertSecret(String orgName, String secretName) {
        var secretPath = toSecretPath(orgName, secretName);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '" + secretPath + "' not found");
        }
        return secretPath;
    }

    private Path createSecretFile(String orgName, String secretName) throws IOException {
        var path = toSecretPath(orgName, secretName);

        if (Files.exists(path)) {
            throw new RuntimeException("Secret '" + orgName + "/" + secretName + "' ('" + path + "') already exists");
        }

        if (Files.notExists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        return path;
    }

    private Path toSecretPath(String orgName, String name) {
        var secretPath = secretStoreDir;
        if (orgName != null) {
            secretPath = secretStoreDir.resolve(orgName);
        }

        return secretPath.resolve(name);
    }

    private static Path assertTmpDir(Path workDir) throws IOException {
        var dir = workDir.resolve("target").resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (Files.notExists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }
}
