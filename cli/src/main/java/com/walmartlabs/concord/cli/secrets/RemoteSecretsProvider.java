package com.walmartlabs.concord.cli.secrets;

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

import com.walmartlabs.concord.cli.AbortException;
import com.walmartlabs.concord.cli.Confirmation;
import com.walmartlabs.concord.cli.Version;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class RemoteSecretsProvider implements SecretsProvider {

    private final Path workDir;
    private final SecretClient secretClient;
    private final boolean confirmAccess;

    public RemoteSecretsProvider(@Nullable Path workDir, String baseUrl, String apiKey, boolean confirmAccess) {
        this.workDir = workDir;
        this.confirmAccess = confirmAccess;

        var apiClient = new DefaultApiClientFactory(requireNonNull(baseUrl))
                .create(ApiClientConfiguration.builder()
                        .apiKey(requireNonNull(apiKey))
                        .build());
        apiClient.setUserAgent("Concord-Cli (%s)".formatted(Version.getVersion()));

        this.secretClient = new SecretClient(apiClient);
    }

    @Override
    public Optional<SecretService.KeyPair> exportKeyAsFile(String orgName, String secretName, String secretPassword) throws Exception {
        return getKeyPair(orgName, secretName, secretPassword)
                .map(keyPair -> {
                    var tmpDir = UncheckedIO.assertTmpDir(assertWorkDir());

                    var publicKeyPath = tmpDir.resolve(secretName + ".pub");
                    UncheckedIO.write(publicKeyPath, keyPair.getPublicKey(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    var privateKeyPath = tmpDir.resolve(secretName);
                    UncheckedIO.write(privateKeyPath, keyPair.getPrivateKey(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    return SecretService.KeyPair.builder()
                            .privateKey(privateKeyPath)
                            .publicKey(publicKeyPath)
                            .build();
                });
    }

    @Override
    public Optional<String> exportAsString(String orgName, String secretName, String secretPassword) throws Exception {
        return getBinaryDataSecret(orgName, secretName, secretPassword)
                .map(BinaryDataSecret::getData)
                .map(String::new);
    }

    @Override
    public Optional<Path> exportAsFile(String orgName, String secretName, String secretPassword) throws Exception {
        return getBinaryDataSecret(orgName, secretName, secretPassword)
                .map(BinaryDataSecret::getData)
                .map(data -> {
                    var tmpDir = UncheckedIO.assertTmpDir(assertWorkDir());
                    var secretPath = tmpDir.resolve(secretName + ".bin");
                    UncheckedIO.write(secretPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    return secretPath;
                });
    }

    @Override
    public Optional<SecretService.UsernamePassword> exportCredentials(String orgName, String secretName, String secretPassword) throws Exception {
        return getUsernamePassword(orgName, secretName, secretPassword)
                .map(secret -> SecretService.UsernamePassword.of(secret.getUsername(), new String(secret.getPassword())));
    }

    @Override
    public SecretService.SecretCreationResult createKeyPair(SecretService.SecretParams secret, SecretService.KeyPair keyPair) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .keyPair(CreateSecretRequest.KeyPair.builder()
                        .publicKey(keyPair.publicKey())
                        .privateKey(keyPair.privateKey())
                        .build())
                .build()));
    }

    @Override
    public SecretService.SecretCreationResult createUsernamePassword(SecretService.SecretParams secret, SecretService.UsernamePassword usernamePassword) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .usernamePassword(CreateSecretRequest.UsernamePassword.of(usernamePassword.username(), usernamePassword.password()))
                .build()));
    }

    @Override
    public SecretService.SecretCreationResult createData(SecretService.SecretParams secret, byte[] data) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .data(data)
                .build()));
    }

    private Optional<KeyPair> getKeyPair(String orgName, String secretName, String secretPassword) throws Exception {
        askForAccessConfirmation(orgName, secretName);

        try {
            return Optional.of(secretClient.getData(orgName, secretName, secretPassword, SecretEntryV2.TypeEnum.KEY_PAIR));
        } catch (com.walmartlabs.concord.client2.SecretNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<BinaryDataSecret> getBinaryDataSecret(String orgName, String secretName, String secretPassword) throws Exception {
        askForAccessConfirmation(orgName, secretName);

        try {
            return Optional.of(secretClient.getData(orgName, secretName, secretPassword, SecretEntryV2.TypeEnum.DATA));
        } catch (com.walmartlabs.concord.client2.SecretNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<UsernamePassword> getUsernamePassword(String orgName, String secretName, String secretPassword) throws Exception {
        askForAccessConfirmation(orgName, secretName);

        try {
            return Optional.of(secretClient.getData(orgName, secretName, secretPassword, SecretEntryV2.TypeEnum.USERNAME_PASSWORD));
        } catch (com.walmartlabs.concord.client2.SecretNotFoundException e) {
            return Optional.empty();
        }
    }

    private static SecretService.SecretCreationResult toResult(SecretOperationResponse response) {
        return SecretService.SecretCreationResult.builder()
                .id(response.getId())
                .password(response.getPassword())
                .build();
    }

    private ImmutableCreateSecretRequest.Builder secretRequest(SecretService.SecretParams params) {
        var builder = CreateSecretRequest.builder()
                .org(params.orgName())
                .name(params.secretName())
                .generatePassword(params.generatePassword())
                .storePassword(params.storePassword());

        var visibility = params.visibility();
        if (visibility != null) {
            builder.visibility(SecretEntryV2.VisibilityEnum.fromValue(visibility.name()));
        }

        if (params.project() != null) {
            builder.addProjectNames(Objects.requireNonNull(params.project()));
        }

        return builder;
    }

    private void askForAccessConfirmation(String orgName, String secretName) throws IOException {
        if (!confirmAccess) {
            return;
        }

        if (!Confirmation.confirm("Fetching remote secret %s/%s. Are you sure you want to proceed? (y/N)".formatted(orgName, secretName))) {
            throw new AbortException();
        }
    }

    private Path assertWorkDir() {
        if (this.workDir == null) {
            throw new RuntimeException("The workDir must be specified for the export functions to work");
        }
        return this.workDir;
    }
}
