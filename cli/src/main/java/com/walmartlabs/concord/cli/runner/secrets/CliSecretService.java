package com.walmartlabs.concord.cli.runner.secrets;

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

import com.walmartlabs.concord.cli.CliConfig.CliConfigContext;
import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.cli.runner.VaultProvider;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.fusesource.jansi.Ansi.ansi;

public class CliSecretService implements SecretService {

    private final List<SecretsProviderRef> secretsProviders;
    private final VaultProvider vaultProvider;
    private final Verbosity verbosity;

    public static CliSecretService create(CliConfigContext cliConfigContext, Path workDir, Verbosity verbosity) {
        var providers = new ArrayList<SecretsProviderRef>();

        var local = cliConfigContext.secrets().local();
        if (local.enabled()) {
            var provider = new FileSecretsProvider(workDir, local.dir());
            providers.add(new SecretsProviderRef("localFile", provider, local.writable()));
        }

        var remote = cliConfigContext.secrets().remote();
        if (remote.enabled()) {
            var provider = new RemoteSecretsProvider(workDir, remote.baseUrl(), remote.apiKey(), remote.confirmAccess());
            providers.add(new SecretsProviderRef("remote", provider, remote.writable()));
        }

        var vault = cliConfigContext.secrets().vault();
        return new CliSecretService(providers, new VaultProvider(vault.dir(), vault.id()), verbosity);
    }

    public CliSecretService(List<SecretsProviderRef> secretsProviders, VaultProvider vaultProvider, Verbosity verbosity) {
        this.secretsProviders = requireNonNull(secretsProviders);
        this.vaultProvider = requireNonNull(vaultProvider);
        this.verbosity = requireNonNull(verbosity);
    }

    @Override
    public SecretService.KeyPair exportKeyAsFile(String orgName, String secretName, String secretPassword) throws Exception {
        for (var ref : secretsProviders) {
            var result = ref.provider().exportKeyAsFile(orgName, secretName, secretPassword);
            if (result.isPresent()) {
                reportSecretFound(orgName, secretName, ref);
                return result.get();
            }
        }
        throw new RuntimeException("Secret not found: %s/%s.".formatted(orgName, secretName));
    }

    @Override
    public String exportAsString(String orgName, String secretName, String secretPassword) throws Exception {
        for (var ref : secretsProviders) {
            var result = ref.provider().exportAsString(orgName, secretName, secretPassword);
            if (result.isPresent()) {
                reportSecretFound(orgName, secretName, ref);
                return result.get();
            }
        }
        throw new RuntimeException("Secret not found: %s/%s.".formatted(orgName, secretName));
    }

    @Override
    public Path exportAsFile(String orgName, String secretName, String secretPassword) throws Exception {
        for (var ref : secretsProviders) {
            var result = ref.provider().exportAsFile(orgName, secretName, secretPassword);
            if (result.isPresent()) {
                reportSecretFound(orgName, secretName, ref);
                return result.get();
            }
        }
        throw new RuntimeException("Secret not found: %s/%s.".formatted(orgName, secretName));
    }

    @Override
    public UsernamePassword exportCredentials(String orgName, String secretName, String secretPassword) throws Exception {
        for (var ref : secretsProviders) {
            var result = ref.provider().exportCredentials(orgName, secretName, secretPassword);
            if (result.isPresent()) {
                reportSecretFound(orgName, secretName, ref);
                return result.get();
            }
        }
        throw new RuntimeException("Secret not found: %s/%s.".formatted(orgName, secretName));
    }

    @Override
    public SecretCreationResult createKeyPair(SecretParams secret, KeyPair keyPair) throws Exception {
        var ref = assertWritableProvider();
        var result = ref.provider().createKeyPair(secret, keyPair);
        reportSecretCreated(secret, ref);
        return result;
    }

    @Override
    public SecretCreationResult createUsernamePassword(SecretParams secret, UsernamePassword usernamePassword) throws Exception {
        var ref = assertWritableProvider();
        var result = ref.provider().createUsernamePassword(secret, usernamePassword);
        reportSecretCreated(secret, ref);
        return result;
    }

    @Override
    public SecretCreationResult createData(SecretParams secret, byte[] data) throws Exception {
        var ref = assertWritableProvider();
        var result = ref.provider().createData(secret, data);
        reportSecretCreated(secret, ref);
        return result;
    }

    @Override
    public String decryptString(String encryptedValue) {
        return vaultProvider.getValue(encryptedValue);
    }

    @Override
    public String encryptString(String orgName, String projectName, String value) {
        throw new UnsupportedOperationException("Encrypting secrets is not supported by concord-cli yet");
    }

    private SecretsProviderRef assertWritableProvider() {
        return secretsProviders.stream().filter(SecretsProviderRef::writable)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No writable secret providers configured"));
    }

    private void reportSecretFound(String orgName, String secretName, SecretsProviderRef ref) {
        if (verbosity.verbose()) {
            System.out.println(ansi().fgBlue().a("Fetched secret ").a(orgName).a("/").a(secretName).a(" from the '").a(ref.name()).a("' provider"));
        }
    }

    private void reportSecretCreated(SecretParams params, SecretsProviderRef ref) {
        if (verbosity.verbose()) {
            System.out.println(ansi().fgBlue().a("Created secret ").a(params.orgName()).a("/").a(params.secretName()).a(" in the '").a(ref.name()).a("' provider"));
        }
    }
}
