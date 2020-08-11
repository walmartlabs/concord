package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

public class AnsibleConfigTest extends AbstractTest {

    @Test
    public void testDefaultConfig() throws Exception {
        Path workDir = tempDir("ansible-config-workdir");
        Path tmpDir = workDir.resolve("tmp");
        Files.createDirectories(tmpDir);

        AnsibleContext context = AnsibleContext.builder()
                .apiBaseUrl("http://localhost:8001")
                .instanceId(UUID.randomUUID())
                .workDir(workDir)
                .tmpDir(tmpDir)
                .build();

        AnsibleConfig cfg = new AnsibleConfig(context);
        cfg.parse(new HashMap<>());

        new AnsibleCallbacks(workDir, tmpDir, false).enrich(cfg);
        new AnsibleLookup(tmpDir).enrich(cfg);

        Path cfgPath = cfg.write();
        assertFile("ansible.cfg", workDir.resolve(cfgPath));
    }
}
