package com.walmartlabs.concord.cli.runner.secrets;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.cli.runner.secrets.UncheckedIO.*;

/**
 * Simple file-based secret provider.
 * Secrets stored unencrypted in ${dir}/${orgName}/${secretName}.
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
    public Optional<SecretService.KeyPair> exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        var publicKey = toSecretPath(orgName, secretName + ".pub");
        var privateKey = toSecretPath(orgName, secretName);

        if (Files.notExists(publicKey) || Files.notExists(privateKey)) {
            return Optional.empty();
        }

        var tmpDir = UncheckedIO.assertTmpDir(workDir);
        var tmpPublicKey = tmpDir.resolve(secretName + ".pub");
        var tmpPrivateKey = tmpDir.resolve(secretName);
        Files.copy(publicKey, tmpPublicKey, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(privateKey, tmpPrivateKey, StandardCopyOption.REPLACE_EXISTING);

        var result = SecretService.KeyPair.builder()
                .privateKey(tmpPrivateKey)
                .publicKey(tmpPublicKey)
                .build();

        return Optional.of(result);
    }

    @Override
    public Optional<String> exportAsString(String orgName, String secretName, String password) throws IOException {
        return getSecret(orgName, secretName)
                .map(UncheckedIO::readAllBytes)
                .map(String::new)
                .map(String::trim);
    }

    @Override
    public Optional<Path> exportAsFile(String orgName, String secretName, String password) throws IOException {
        return getSecret(orgName, secretName)
                .map(path -> {
                    var tmpDir = assertTmpDir(workDir);
                    var dest = createTempFile(tmpDir, "file", ".bin");
                    copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    return dest;
                });
    }

    @Override
    public Optional<SecretService.UsernamePassword> exportCredentials(String orgName, String secretName, String secretPassword) {
        return getSecret(orgName, secretName).map(path -> {
            try {
                var data = objectMapper.readTree(path.toFile());

                var username = Optional.ofNullable(data.get("username")).map(JsonNode::asText)
                        .orElseThrow(() -> new IllegalStateException("Secret %s/%s is missing the username field".formatted(orgName, secretName)));
                var password = Optional.ofNullable(data.get("password")).map(JsonNode::asText)
                        .orElseThrow(() -> new IllegalStateException("Secret %s/%s is missing the password field".formatted(orgName, secretName)));

                return SecretService.UsernamePassword.of(username, password);
            } catch (IOException e) {
                throw new RuntimeException("Invalid secret '%s/%s' ('%s') format: %s".formatted(orgName, secretName, path, e.getMessage()));
            }
        });
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

    private Optional<Path> getSecret(String orgName, String secretName) {
        var secretPath = toSecretPath(orgName, secretName);
        if (Files.notExists(secretPath)) {
            throw new RuntimeException("Secret '%s' not found".formatted(secretPath));
        }
        return Optional.of(secretPath);
    }

    private Path createSecretFile(String orgName, String secretName) throws IOException {
        var path = toSecretPath(orgName, secretName);

        if (Files.exists(path)) {
            throw new RuntimeException("Secret '%s/%s' ('%s') already exists".formatted(orgName, secretName, path));
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

        var result = secretPath.resolve(name);

        if (!result.normalize().startsWith(secretStoreDir)) {
            throw new IllegalArgumentException("Invalid secret name: %s/%s".formatted(orgName, name));
        }

        return result;
    }
}
