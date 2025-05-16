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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class CliConfigTest {

    @Test
    public void parse() throws Exception {
        var cfg = load("testConfig.yaml");
        assertEquals("foo", cfg.secrets().vault().id());
    }

    @Test
    public void checkDefaults() throws Exception {
        var cfg = load("configWithDefaults.yaml");
        assertNotNull(cfg.secrets().vault().dir());
        assertTrue(cfg.secrets().vault().dir().toString().contains("/vaults"));
    }

    @Test
    public void withOverrides() throws Exception {
        var cfg = load("testConfig.yaml")
                .withOverrides(new CliConfig.Overrides(Path.of("/barbaz"), Path.of("/foobar"), "qux"));
        assertEquals("/barbaz", cfg.secrets().local().dir().toString());
        assertEquals("/foobar", cfg.secrets().vault().dir().toString());
        assertEquals("qux", cfg.secrets().vault().id());
    }

    private static CliConfig load(String resource) throws IOException, URISyntaxException {
        var src = Paths.get(requireNonNull(CliConfigTest.class.getResource(resource)).toURI());;
        return CliConfig.load(src);
    }
}
