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

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class CliSecretService implements SecretService {

    private final SecretsProvider secretsProvider;
    private final VaultProvider vaultProvider;

    public CliSecretService(SecretsProvider secretsProvider, VaultProvider vaultProvider) {
        this.secretsProvider = requireNonNull(secretsProvider);
        this.vaultProvider = requireNonNull(vaultProvider);
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String orgName, String secretName, String password) throws Exception {
        return secretsProvider.exportKeyAsFile(orgName, secretName, password);
    }

    @Override
    public String exportAsString(String orgName, String secretName, String password) throws IOException {
        return secretsProvider.exportAsString(orgName, secretName, password);
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String password) throws IOException {
        return secretsProvider.exportAsFile(orgName, secretName, password);
    }

    @Override
    public String decryptString(String encryptedValue) {
        return vaultProvider.getValue(encryptedValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public UsernamePassword exportCredentials(String orgName, String secretName, String password) {
        return secretsProvider.exportCredentials(orgName, secretName, password);
    }

    @Override
    public String encryptString(String orgName, String projectName, String value) {
        throw new UnsupportedOperationException("Encrypting secrets is not supported by concord-cli yet");
    }

    @Override
    public SecretCreationResult createKeyPair(SecretParams secret, KeyPair keyPair) throws Exception {
        return secretsProvider.createKeyPair(secret, keyPair);
    }

    @Override
    public SecretCreationResult createUsernamePassword(SecretParams secret, UsernamePassword usernamePassword) throws Exception {
        return secretsProvider.createUsernamePassword(secret, usernamePassword);
    }

    @Override
    public SecretCreationResult createData(SecretParams secret, byte[] data) throws Exception {
        return secretsProvider.createData(secret, data);
    }
}
