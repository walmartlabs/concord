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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CliConfigTest {

    @Test
    public void parse() throws Exception {
        var cfg = load("testConfig.yaml");
        assertEquals("foo", cfg.secrets().localFiles().vaultId());
    }

    @Test
    public void checkDefaults() throws Exception {
        var cfg = load("configWithDefaults.yaml");
        assertNotNull(cfg.secrets().localFiles().vaultDir());
        assertTrue(cfg.secrets().localFiles().vaultDir().toString().contains("/vaults"));
    }

    @Test
    public void withOverrides() throws Exception {
        var cfg = load("testConfig.yaml")
                .withOverrides(new CliConfig.Overrides(Path.of("/barbaz"), Path.of("/foobar"), "qux"));
        assertEquals("/barbaz", cfg.secrets().localFiles().secretStoreDir().toString());
        assertEquals("/foobar", cfg.secrets().localFiles().vaultDir().toString());
        assertEquals("qux", cfg.secrets().localFiles().vaultId());
    }

    private static CliConfig load(String resource) throws IOException {
        var src = CliConfigTest.class.getResource(resource);
        assertNotNull(src, "Resource not found: " + resource);
        return new YAMLMapper().readValue(src, CliConfig.class);
    }
}
