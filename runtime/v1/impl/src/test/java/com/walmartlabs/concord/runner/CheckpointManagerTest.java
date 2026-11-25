package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckpointManagerTest {

    @TempDir
    Path workDir;

    @Test
    void excludesGeneratedPolicyFromUploadedCheckpoint() throws Exception {
        Path systemDir = Files.createDirectories(workDir.resolve(".concord"));
        Files.writeString(systemDir.resolve("policy.json"), "current-policy");
        Files.writeString(systemDir.resolve("state"), "state");
        Path attachments = Files.createDirectories(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));
        Files.createDirectories(attachments.resolve(Constants.Files.JOB_STATE_DIR_NAME));
        Files.writeString(attachments.resolve("policy.json"), "user-file");

        UUID instanceId = UUID.randomUUID();
        ProcessApiClient client = mock(ProcessApiClient.class);
        doAnswer(invocation -> {
            Map<String, Object> data = invocation.getArgument(1);
            Map<String, String> entries = new HashMap<>();
            try (ZipInputStream zip = new ZipInputStream((InputStream) data.get("data"))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    entries.put(entry.getName(), new String(zip.readAllBytes()));
                }
            }
            assertFalse(entries.containsKey(".concord/policy.json"));
            assertEquals("state", entries.get(".concord/state"));
            assertEquals("user-file", entries.get(Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/policy.json"));
            assertEquals("test", entries.get(Constants.Files.CHECKPOINT_META_FILE_NAME));
            return null;
        }).when(client).uploadCheckpoint(eq(instanceId), anyMap());

        new CheckpointManager(instanceId, client).process(UUID.randomUUID(), UUID.randomUUID(), "test", workDir);

        verify(client).uploadCheckpoint(eq(instanceId), anyMap());
        assertEquals("current-policy", Files.readString(systemDir.resolve("policy.json")));
    }
}
