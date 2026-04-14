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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class CliConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    public void parse() throws Exception {
        var cfg = load("testConfig.yaml");
        var defaultCtx = cfg.contexts().get("default");
        assertNotNull(defaultCtx);
        assertEquals("foo", defaultCtx.secrets().vault().id());
    }

    @Test
    public void checkDefaults() throws Exception {
        var cfg = load("configWithDefaults.yaml");
        var defaultCtx = cfg.contexts().get("default");
        assertNotNull(defaultCtx);
        assertNotNull(defaultCtx.secrets().vault().dir());
        assertTrue(defaultCtx.secrets().vault().dir().toString().contains("/vaults"));
    }

    @Test
    public void withOverrides() throws Exception {
        var cfg = load("testConfig.yaml");
        var defaultCtx = cfg.contexts().get("default");
        assertNotNull(defaultCtx);
        var ctxWithOverrides = defaultCtx.withOverrides(new CliConfig.Overrides(Path.of("/barbaz"), Path.of("/foobar"), "qux"));
        assertEquals("/barbaz", ctxWithOverrides.secrets().local().dir().toString());
        assertEquals("/foobar", ctxWithOverrides.secrets().vault().dir().toString());
        assertEquals("qux", ctxWithOverrides.secrets().vault().id());
    }

    @Test
    public void multiContexts() throws Exception {
        var cfg = load("multiContextConfig.yaml");
        var anotherCtx = cfg.contexts().get("another");
        assertNotNull(anotherCtx);
        assertEquals("bar", anotherCtx.secrets().vault().id());
        assertEquals("qux", anotherCtx.secrets().vault().dir().toString());
    }

    @Test
    public void missingContextWithoutUserConfig() throws Exception {
        var homeDir = tempDir.resolve("missing-context-home");

        var e = withUserHome(homeDir, () -> assertThrows(CliConfig.MissingContextException.class,
                () -> CliConfig.loadOrThrow(new Verbosity(new boolean[0]), "another", new CliConfig.Overrides(null, null, null))));

        assertTrue(e.getMessage().contains("Configuration context not found: another"));
        assertTrue(e.getMessage().contains("only the built-in 'default' context is available"));
    }

    @Test
    public void missingContextWithUserConfig() throws Exception {
        var homeDir = tempDir.resolve("configured-home");
        var configDir = homeDir.resolve(".concord");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("cli.yaml"), """
                contexts:
                  default: {}
                """);

        var e = withUserHome(homeDir, () -> assertThrows(CliConfig.MissingContextException.class,
                () -> CliConfig.loadOrThrow(new Verbosity(new boolean[0]), "another", new CliConfig.Overrides(null, null, null))));

        assertEquals("Configuration context not found: another. Check the CLI configuration file.", e.getMessage());
    }

    private static CliConfig load(String resource) throws IOException, URISyntaxException {
        var src = Paths.get(requireNonNull(CliConfigTest.class.getResource(resource)).toURI());
        return CliConfig.loadConfigFile(src);
    }

    private static <T> T withUserHome(Path userHome, java.util.concurrent.Callable<T> action) throws Exception {
        var originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        try {
            return action.call();
        } finally {
            if (originalUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalUserHome);
            }
        }
    }
}
