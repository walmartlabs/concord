package com.walmartlabs.concord.server.process.state;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class ProcessStateManagerTest extends AbstractDaoTest {

    @Test
    public void testUpdateState() throws Exception {
        ProcessKey processKey = new ProcessKey(UUID.randomUUID(), OffsetDateTime.now());

        Path baseDir = Files.createTempDirectory("testImport");

        writeTempFile(baseDir.resolve("file-1"), "123".getBytes());
        writeTempFile(baseDir.resolve("file-2"), "456".getBytes());

        //
        ProcessKeyCache processKeyCache = new ProcessKeyCache(new ProcessQueueDao(getConfiguration(), new ConcordObjectMapper(new ObjectMapper())));
        ProcessConfiguration stateCfg = new ProcessConfiguration(Duration.ofMillis(24 * 60 * 60 * 1000), Collections.singletonList(Constants.Files.CONFIGURATION_FILE_NAME));
        ProcessStateManager stateManager = new ProcessStateManager(getConfiguration(), mock(SecretStoreConfiguration.class), stateCfg, mock(PolicyManager.class), mock(ProcessLogManager.class), processKeyCache);
        stateManager.importPath(processKey, null, baseDir, (p, attrs) -> true);

        Path tmpDir = Files.createTempDirectory("testExport");

        boolean result = stateManager.export(processKey, copyTo(tmpDir));
        assertTrue(result);
        assertFileContent("123", tmpDir.resolve("file-1"));
        assertFileContent("456", tmpDir.resolve("file-2"));

        // --- update

        writeTempFile(baseDir.resolve("file-1"), "123-up".getBytes());

        stateManager.importPath(processKey, null, baseDir, (p, attrs) -> true);

        result = stateManager.export(processKey, copyTo(tmpDir));
        assertTrue(result);
        assertFileContent("123-up", tmpDir.resolve("file-1"));
        assertFileContent("456", tmpDir.resolve("file-2"));
    }

    @Ignore
    @Test
    public void testLargeImport() throws Exception {
        ProcessKey processKey = new ProcessKey(UUID.randomUUID(), OffsetDateTime.now());

        int files = 100;
        int chunkSize = 1024 * 1024;
        int fileSize = 10 * chunkSize;

        byte[] ab = new byte[chunkSize];
        Arrays.fill(ab, (byte) 0);

        Path baseDir = Files.createTempDirectory("test");
        for (int i = 0; i < files; i++) {
            Path p = baseDir.resolve("file" + i);
            try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE)) {
                for (int j = 0; j < fileSize; j += chunkSize) {
                    out.write(ab);
                }
            }
        }

        ProcessKeyCache processKeyCache = new ProcessKeyCache(new ProcessQueueDao(getConfiguration(), new ConcordObjectMapper(new ObjectMapper())));
        ProcessConfiguration stateCfg = new ProcessConfiguration(Duration.ofMillis(24 * 60 * 60 * 1000), Collections.singletonList(Constants.Files.CONFIGURATION_FILE_NAME));
        ProcessStateManager stateManager = new ProcessStateManager(getConfiguration(), mock(SecretStoreConfiguration.class), stateCfg, mock(PolicyManager.class), mock(ProcessLogManager.class), processKeyCache);
        stateManager.importPath(processKey, "/", baseDir, (p, attrs) -> true);
    }

    private static void assertFileContent(String expected, Path f) throws IOException {
        String str = com.google.common.io.Files.asCharSource(f.toFile(), Charsets.UTF_8).read();
        assertEquals(expected, str);
    }

    private static void writeTempFile(Path p, byte[] ab) throws IOException {
        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE)) {
            out.write(ab);
        }
    }
}
