package com.walmartlabs.concord.runtime.v25.runner;

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

import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RemoteCheckpointStoreTest {

    @TempDir
    Path workDirectory;

    @Test
    void archiveIncludesStateAndSystemDirectory() throws Exception {
        var state = workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME).resolve("runtime-v2.5.state");
        Files.createDirectories(state.getParent());
        Files.writeString(state, "state");
        var policy = workDirectory.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME).resolve("policy.json");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, "deny");
        var sensitiveData = workDirectory.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_SESSION_FILES_DIR_NAME).resolve(Constants.Files.SENSITIVE_DATA_FILE_NAME);
        Files.createDirectories(sensitiveData.getParent());
        Files.writeString(sensitiveData, "[\"persisted-secret\"]");

        var archive = RemoteCheckpointStore.archive(state);
        try (var zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry(Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/"
                    + Constants.Files.JOB_STATE_DIR_NAME + "/runtime-v2.5.state"));
            assertNull(zip.getEntry(Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/"
                    + Constants.Files.JOB_SESSION_FILES_DIR_NAME + "/"
                    + Constants.Files.SENSITIVE_DATA_FILE_NAME),
                    "checkpoint archives must not carry the sensitive-data session file");
            assertNotNull(zip.getEntry(".concord/policy.json"));
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
