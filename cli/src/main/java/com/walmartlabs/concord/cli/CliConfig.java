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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.fusesource.jansi.Ansi.ansi;

public record CliConfig(Map<String, CliConfigContext> contexts) {

    private static final Logger log = LoggerFactory.getLogger(CliConfig.class);

    public static CliConfig.CliConfigContext load(Verbosity verbosity, String context, Overrides overrides) {
        Path baseDir = Paths.get(System.getProperty("user.home"), ".concord");
        Path cfgFile = baseDir.resolve("cli.yaml");
        if (!Files.exists(cfgFile)) {
            cfgFile = baseDir.resolve("cli.yml");
        }
        if (!Files.exists(cfgFile)) {
            CliConfig cfg = CliConfig.create();
            return assertCliConfigContext(cfg, context).withOverrides(overrides);
        }

        if (verbosity.verbose()) {
            log.info("Using CLI configuration file: {} (\"{}\" context)", cfgFile, context);
        }

        try {
            CliConfig cfg = loadConfigFile(cfgFile);
            return assertCliConfigContext(cfg, context).withOverrides(overrides);
        } catch (Exception e) {
            handleCliConfigErrorAndBail(cfgFile.toAbsolutePath().toString(), e);
            throw new IllegalStateException("should be unreachable");
        }
    }

    private static void handleCliConfigErrorAndBail(String cfgPath, Throwable e) {
        // unwrap runtime exceptions
        if (e instanceof RuntimeException ex) {
            if (ex.getCause() instanceof IllegalArgumentException) {
                e = ex.getCause();
            }
        }

        // handle YAML errors
        if (e instanceof IllegalArgumentException) {
            if (e.getCause() instanceof UnrecognizedPropertyException ex) {
                System.out.println(ansi().fgRed().a("Invalid format of the CLI configuration file ").a(cfgPath).a(". ").a(ex.getMessage()));
                System.exit(1);
            }
            System.out.println(ansi().fgRed().a("Invalid format of the CLI configuration file ").a(cfgPath).a(". ").a(e.getMessage()));
            System.exit(1);
        }

        // all other errors
        System.out.println(ansi().fgRed().a("Failed to read the CLI configuration file ").a(cfgPath).a(". ").a(e.getMessage()));
        System.exit(1);
    }

    private static CliConfig.CliConfigContext assertCliConfigContext(CliConfig config, String context) {
        CliConfig.CliConfigContext result = config.contexts().get(context);
        if (result == null) {
            System.out.println(ansi().fgRed().a("Configuration context not found: ").a(context).a(". Check the CLI configuration file."));
            System.exit(1);
        }
        return result;
    }

    @VisibleForTesting
    static CliConfig loadConfigFile(Path path) throws IOException {
        var mapper = new YAMLMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        JsonNode defaults = mapper.readTree(readDefaultConfig());

        JsonNode cfg;
        try (var reader = Files.newBufferedReader(path)) {
            cfg = mapper.readTree(reader);
        }

        // merge the loaded config file with the default built-in config
        var cfgWithDefaults = mapper.updateValue(defaults, cfg);

        // merge each non-default context with the default context
        var contexts = assertContexts(cfgWithDefaults);

        var defaultCtx = contexts.get("default");
        if (defaultCtx == null) {
            throw new IllegalArgumentException("Missing 'default' context.");
        }

        contexts.fieldNames().forEachRemaining(ctxName -> {
            if ("default".equals(ctxName)) {
                return;
            }

            var ctx = contexts.get(ctxName);
            try {
                var mergedCtx = mapper.updateValue(defaultCtx, ctx);
                contexts.set(ctxName, mergedCtx);
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            }
        });

        return mapper.convertValue(cfgWithDefaults, CliConfig.class);
    }

    private static ObjectNode assertContexts(JsonNode cfg) {
        var maybeContexts = cfg.get("contexts");
        if (maybeContexts == null) {
            throw new IllegalArgumentException("Missing 'contexts' object.");
        }
        if (!maybeContexts.isObject()) {
            throw new IllegalArgumentException("The 'contexts' field must be an object.");
        }
        return (ObjectNode) maybeContexts;
    }

    public static CliConfig create() {
        var mapper = new YAMLMapper();
        try {
            return mapper.readValue(readDefaultConfig(), CliConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Can't parse the default CLI config file. " + e.getMessage());
        }
    }

    public record Overrides(@Nullable Path secretStoreDir, @Nullable Path vaultDir, @Nullable String vaultId) {
    }

    public record CliConfigContext(@Nullable RemoteRunConfiguration remoteRun, SecretsConfiguration secrets) {

        public CliConfigContext withOverrides(@Nullable Overrides overrides) {
            if (overrides == null) {
                return this;
            }
            var remoteRun = this.remoteRun();
            var secrets = this.secrets().withOverrides(overrides);
            return new CliConfigContext(remoteRun, secrets);
        }
    }

    public record SecretRef(String orgName, String secretName) {

        public SecretRef(String orgName, String secretName) {
            this.orgName = orgName == null ? "Default" : orgName;
            if (this.orgName.isBlank()) {
                throw new IllegalArgumentException("'orgName' is required");
            }
            this.secretName = requireNonNull(secretName);
            if (this.secretName.isBlank()) {
                throw new IllegalArgumentException("'secretName' is required");
            }
        }
    }

    public record RemoteRunConfiguration(@Nullable String baseUrl, @Nullable SecretRef apiKeyRef) {
    }

    public record SecretsConfiguration(VaultConfiguration vault,
                                       FileSecretsProviderConfiguration local,
                                       RemoteSecretsProviderConfiguration remote) {

        public SecretsConfiguration withOverrides(@Nullable Overrides overrides) {
            if (overrides == null) {
                return this;
            }
            var vault = this.vault().withOverrides(overrides);
            var localFiles = this.local().withOverrides(overrides);
            return new SecretsConfiguration(vault, localFiles, this.remote);
        }

        public record VaultConfiguration(Path dir, String id) {

            public VaultConfiguration withOverrides(@Nullable Overrides overrides) {
                if (overrides == null) {
                    return this;
                }
                return new VaultConfiguration(
                        Optional.ofNullable(overrides.vaultDir()).orElse(this.dir()),
                        Optional.ofNullable(overrides.vaultId()).orElse(this.id()));
            }
        }

        public record FileSecretsProviderConfiguration(boolean enabled, boolean writable, Path dir) {

            public FileSecretsProviderConfiguration withOverrides(@Nullable Overrides overrides) {
                if (overrides == null) {
                    return this;
                }
                return new FileSecretsProviderConfiguration(
                        this.enabled,
                        this.writable,
                        Optional.ofNullable(overrides.secretStoreDir()).orElse(this.dir()));
            }
        }

        public record RemoteSecretsProviderConfiguration(boolean enabled,
                                                         boolean writable,
                                                         @Nullable String baseUrl,
                                                         @Nullable String apiKey,
                                                         boolean confirmAccess) {
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
