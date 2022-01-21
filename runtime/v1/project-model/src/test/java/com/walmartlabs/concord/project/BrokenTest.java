package com.walmartlabs.concord.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.project.yaml.YamlParserException;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class BrokenTest {

    @Test
    public void testDir() {
        assertThrows(YamlParserException.class, () -> loadProject("brokenMain"));
    }

    @Test
    public void testStream() {
        assertThrows(YamlParserException.class, () -> {
            ProjectLoader l = new ProjectLoader(mock(ImportManager.class));
            l.loadProject(ClassLoader.getSystemResourceAsStream("brokenMain/concord.yml"));
        });
    }

    @Test
    public void testProfiles() {
        assertThrows(YamlParserException.class, () -> loadProject("brokenProfiles"));
    }

    @Test
    public void testFlows() {
        assertThrows(YamlParserException.class, () -> loadProject("brokenFlows"));
    }

    private static void loadProject(String name) throws Exception {
        ProjectLoader l = new ProjectLoader(mock(ImportManager.class));
        l.loadProject(Paths.get(ClassLoader.getSystemResource(name).toURI()), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
    }
}
