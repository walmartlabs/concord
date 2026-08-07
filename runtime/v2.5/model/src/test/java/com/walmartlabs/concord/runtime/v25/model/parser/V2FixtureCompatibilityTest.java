package com.walmartlabs.concord.runtime.v25.model.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2FixtureCompatibilityTest {

    private static final List<String> SUPPORTED_V2_FIXTURES = List.of(
            "000.1.yml", "000.2.yml", "002.1.yml", "003.yml", "004.yml", "006.yml", "007.yml",
            "008.yml", "009.yml", "010.yml", "012.yml", "013.yml", "014.1.yml", "015.yml", "016.yml",
            "017.yml", "018.yml", "019.yml", "020.yml", "args-order.concord.yml", "validationConfig.yml");

    @Test
    void parsesTheSupportedRuntimeV2FixtureCorpus() {
        var resources = Path.of("..", "..", "v2", "model", "src", "test", "resources").toAbsolutePath();
        assertTrue(Files.isDirectory(resources), () -> "Missing runtime-v2 fixtures at " + resources);

        for (var fixture : SUPPORTED_V2_FIXTURES) {
            assertDoesNotThrow(() -> {
                var source = Files.readString(resources.resolve(fixture), StandardCharsets.UTF_8)
                        .replace("concord-v2", "concord-v2.5");
                new DefinitionParser().parse(fixture,
                        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
            }, fixture);
        }
    }
}
