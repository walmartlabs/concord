package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StateArchiveTest {

    @TempDir
    Path workDir;

    @Test
    void excludesGeneratedPolicyWithoutRemovingItFromRunningWorkspace() throws Exception {
        var systemDir = Files.createDirectories(workDir.resolve(".concord"));
        Files.writeString(systemDir.resolve("policy.json"), "current-policy");
        Files.writeString(systemDir.resolve("state"), "state");

        try (var archive = new StateArchive();
             var file = archive.withSystemDirectory(workDir).zip();
             var zip = new ZipFile.Builder().setPath(file.path()).get()) {
            assertNull(zip.getEntry(".concord/policy.json"));
            var state = zip.getEntry(".concord/state");
            assertNotNull(state);
            try (var in = zip.getInputStream(state)) {
                assertEquals("state", new String(in.readAllBytes()));
            }
        }

        assertEquals("current-policy", Files.readString(systemDir.resolve("policy.json")));
    }
}
