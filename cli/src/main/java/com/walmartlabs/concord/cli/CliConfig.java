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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

public record CliConfig(SecretsConfiguration secrets) {

    public static CliConfig load(Path path) throws IOException {
        var mapper = new YAMLMapper();

        JsonNode defaults = mapper.readTree(readDefaultConfig());

        JsonNode cfg;
        try (var reader = Files.newBufferedReader(path)) {
            cfg = mapper.readTree(reader);
        }

        var merged = mapper.updateValue(defaults, cfg);
        return mapper.convertValue(merged, CliConfig.class);
    }

    public static CliConfig create() {
        var mapper = new YAMLMapper();
        try {
            return mapper.readValue(readDefaultConfig(), CliConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Can't parse the default CLI config file. " + e.getMessage());
        }
    }

    public CliConfig withOverrides(Overrides overrides) {
        var secrets = this.secrets().withOverrides(overrides);
        return new CliConfig(secrets);
    }

    public record Overrides(@Nullable Path secretStoreDir, @Nullable Path vaultDir, @Nullable String vaultId) {
    }

    public record SecretsConfiguration(VaultConfiguration vault,
                                       FileSecretsProviderConfiguration local,
                                       RemoteSecretsProviderConfiguration remote) {

        public SecretsConfiguration withOverrides(Overrides overrides) {
            var vault = this.vault().withOverrides(overrides);
            var localFiles = this.local().withOverrides(overrides);
            return new SecretsConfiguration(vault, localFiles, this.remote);
        }

        public record VaultConfiguration(Path dir, String id) {

            public VaultConfiguration withOverrides(Overrides overrides) {
                return new VaultConfiguration(
                        Optional.ofNullable(overrides.vaultDir()).orElse(this.dir()),
                        Optional.ofNullable(overrides.vaultId()).orElse(this.id()));
            }
        }

        public record FileSecretsProviderConfiguration(boolean enabled, boolean writable, Path dir) {

            public FileSecretsProviderConfiguration withOverrides(Overrides overrides) {
                return new FileSecretsProviderConfiguration(
                        this.enabled,
                        this.writable,
                        Optional.ofNullable(overrides.secretStoreDir()).orElse(this.dir()));
            }
        }

        public record RemoteSecretsProviderConfiguration(boolean enabled,
                                                         boolean writable,
                                                         @Nullable String baseUrl,
                                                         @Nullable String apiKey) {
        }
    }

    private static String readDefaultConfig() {
        try (var in = CliConfig.class.getResourceAsStream("defaultCliConfig.yaml")) {
            if (in == null) {
                throw new IllegalStateException("defaultCliConfig.yaml resource not found");
            }
            var ab = in.readAllBytes();
            var s = new String(ab, UTF_8);

            var dotConcordPath = Paths.get(System.getProperty("user.home")).resolve(".concord");
            return s.replace("${configDir}", dotConcordPath.normalize().toAbsolutePath().toString());
        } catch (IOException e) {
            throw new IllegalStateException("Can't load the default CLI config file. " + e.getMessage());
        }
    }
}
