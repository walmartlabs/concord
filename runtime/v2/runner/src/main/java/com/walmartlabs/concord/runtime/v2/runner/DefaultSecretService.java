package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.SecretNotFoundException;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.sdk.Secret;

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class DefaultSecretService implements SecretService {

    private final SecretClient secretClient;
    private final FileService fileService;
    private final InstanceId instanceId;

    @Inject
    public DefaultSecretService(RunnerConfiguration cfg, ApiClient apiClient, FileService fileService, InstanceId instanceId) {
        this.secretClient = new SecretClient(apiClient, cfg.api().retryCount(), cfg.api().retryInterval());
        this.fileService = fileService;
        this.instanceId = instanceId;
    }

    @Override
    public String exportAsString(String orgName, String secretName, String password) throws Exception {
        BinaryDataSecret s = get(orgName, secretName, password, SecretEntryV2.TypeEnum.DATA);
        return new String(s.getData());
    }

    @Override
    public KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        com.walmartlabs.concord.common.secret.KeyPair kp = get(orgName, secretName, password, SecretEntryV2.TypeEnum.KEY_PAIR);

        Path tmpDir = fileService.createTempDirectory("secret-service");

        Path privateKey = Files.createTempFile(tmpDir, "private", ".key");
        Files.write(privateKey, kp.getPrivateKey());

        Path publicKey = Files.createTempFile(tmpDir, "public", ".key");
        Files.write(publicKey, kp.getPublicKey());

        return KeyPair.builder()
                .publicKey(publicKey)
                .privateKey(privateKey)
                .build();
    }

    @Override
    public UsernamePassword exportCredentials(String orgName, String secretName, String password) throws Exception {
        com.walmartlabs.concord.common.secret.UsernamePassword up = get(orgName, secretName, password, SecretEntryV2.TypeEnum.USERNAME_PASSWORD);
        return UsernamePassword.of(up.getUsername(), new String(up.getPassword()));
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password) throws Exception {
        BinaryDataSecret bds = get(orgName, secretName, password, SecretEntryV2.TypeEnum.DATA);

        Path p = fileService.createTempFile("secret-service-file", ".bin");
        Files.write(p, bds.getData());

        return p;
    }

    @Override
    public String decryptString(String encryptedValue) throws Exception {
        byte[] input;

        try {
            input = DatatypeConverter.parseBase64Binary(encryptedValue);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid encrypted string value, please verify that it was specified/copied correctly: " + e.getMessage());
        }

        return new String(secretClient.decryptString(instanceId.getValue(), input));
    }

    @Override
    public String encryptString(String orgName, String projectName, String value) throws Exception {
        return secretClient.encryptString(orgName, projectName, value);
    }

    @Override
    public SecretCreationResult createKeyPair(SecretParams secret, KeyPair keyPair) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .keyPair(CreateSecretRequest.KeyPair.builder()
                        .publicKey(keyPair.publicKey())
                        .privateKey(keyPair.privateKey())
                        .build())
                .build()));
    }

    @Override
    public SecretCreationResult createUsernamePassword(SecretParams secret, UsernamePassword usernamePassword) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .usernamePassword(CreateSecretRequest.UsernamePassword.of(usernamePassword.username(), usernamePassword.password()))
                .build()));
    }

    @Override
    public SecretCreationResult createData(SecretParams secret, byte[] data) throws Exception {
        return toResult(secretClient.createSecret(secretRequest(secret)
                .data(data)
                .build()));
    }

    private static SecretCreationResult toResult(SecretOperationResponse response) {
        return SecretCreationResult.builder()
                .id(response.getId())
                .password(response.getPassword())
                .build();
    }

    private <T extends Secret> T get(String orgName, String secretName, String password, SecretEntryV2.TypeEnum type) throws Exception {
        try {
            return secretClient.getData(orgName, secretName, password, type);
        } catch (com.walmartlabs.concord.client2.SecretNotFoundException e) {
            throw new SecretNotFoundException(e.getOrgName(), e.getSecretName());
        }
    }

    private ImmutableCreateSecretRequest.Builder secretRequest(SecretParams secret) {
        SecretParams.Visibility visibility = secret.visibility();
        ImmutableCreateSecretRequest .Builder result = CreateSecretRequest.builder()
                .org(secret.orgName())
                .name(secret.secretName())
                .generatePassword(secret.generatePassword())
                .storePassword(secret.storePassword())
                .visibility(visibility != null ? SecretEntryV2.VisibilityEnum.fromValue(visibility.name()) : null);

        if (secret.project() != null) {
            result.addProjectNames(Objects.requireNonNull(secret.project()));
        }

        return result;
    }
}
