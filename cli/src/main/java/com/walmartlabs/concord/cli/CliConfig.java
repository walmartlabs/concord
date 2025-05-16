package com.walmartlabs.concord.cli;

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

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public record CliConfig(SecretsConfiguration secrets) {

    public static CliConfig create() {
        var secrets = SecretsConfiguration.create();
        return new CliConfig(secrets);
    }

    public CliConfig withOverrides(Overrides overrides) {
        var secrets = this.secrets().withOverrides(overrides);
        return new CliConfig(secrets);
    }

    public CliConfig(@Nullable SecretsConfiguration secrets) {
        this.secrets = Optional.ofNullable(secrets).orElseGet(SecretsConfiguration::create);
    }

    public record Overrides(@Nullable Path secretStoreDir, @Nullable Path vaultDir, @Nullable String vaultId) {
    }

    public record SecretsConfiguration(FileSecretsProviderConfiguration localFiles) {

        public static SecretsConfiguration create() {
            var localFiles = FileSecretsProviderConfiguration.create();
            return new SecretsConfiguration(localFiles);
        }

        public SecretsConfiguration withOverrides(Overrides overrides) {
            var localFiles = this.localFiles().withOverrides(overrides);
            return new SecretsConfiguration(localFiles);
        }

        public record FileSecretsProviderConfiguration(Path secretStoreDir, Path vaultDir, String vaultId) {

            public static FileSecretsProviderConfiguration create() {
                return new FileSecretsProviderConfiguration(null, null, null);
            }

            public FileSecretsProviderConfiguration(Path secretStoreDir, Path vaultDir, String vaultId) {
                this.secretStoreDir = Optional.ofNullable(secretStoreDir)
                        .orElseGet(() -> dotConcordPath("secrets"));

                this.vaultDir = Optional.ofNullable(vaultDir)
                        .orElseGet(() -> dotConcordPath("vaults"));

                this.vaultId = Optional.ofNullable(vaultId)
                        .orElse("default");
            }

            public FileSecretsProviderConfiguration withOverrides(Overrides overrides) {
                return new FileSecretsProviderConfiguration(
                        Optional.ofNullable(overrides.secretStoreDir()).orElse(this.secretStoreDir()),
                        Optional.ofNullable(overrides.vaultDir()).orElse(this.vaultDir()),
                        Optional.ofNullable(overrides.vaultId()).orElse(this.vaultId()));
            }
        }
    }

    private static Path dotConcordPath(String path) {
        return Paths.get(System.getProperty("user.home")).resolve(".concord").resolve(path);
    }
}
