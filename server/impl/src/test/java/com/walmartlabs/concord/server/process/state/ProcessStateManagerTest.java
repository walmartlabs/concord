package com.walmartlabs.concord.server.process.state;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.common.DateTimeUtils;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Disabled("requires a local DB instance")
public class ProcessStateManagerTest extends AbstractDaoTest {

    @Test
    public void testUpdateState() throws Exception {
        ProcessKey processKey = ProcessKey.random();

        Path baseDir = Files.createTempDirectory("testImport");

        writeTempFile(baseDir.resolve("file-1"), "123".getBytes());
        writeTempFile(baseDir.resolve("file-2"), "456".getBytes());

        //
        ProcessKeyCache processKeyCache = new ProcessKeyCache(new ProcessQueueDao(getConfiguration(), new ConcordObjectMapper(new ObjectMapper())));
        ProcessConfiguration stateCfg = new ProcessConfiguration(Duration.of(24, ChronoUnit.HOURS), Collections.singletonList(Constants.Files.CONFIGURATION_FILE_NAME));
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

    @Test
    public void testLargeImport() throws Exception {
        ProcessKey processKey = ProcessKey.random();

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
        ProcessConfiguration stateCfg = new ProcessConfiguration(Duration.of(24, ChronoUnit.HOURS), Collections.singletonList(Constants.Files.CONFIGURATION_FILE_NAME));
        ProcessStateManager stateManager = new ProcessStateManager(getConfiguration(), mock(SecretStoreConfiguration.class), stateCfg, mock(PolicyManager.class), mock(ProcessLogManager.class), processKeyCache);
        stateManager.importPath(processKey, "/", baseDir, (p, attrs) -> true);
    }

    @Test
    @Disabled("Used to reproduce the timestamp truncation issue when PG rounds up nanos while JOOQ truncates")
    public void testCreatedAtNanoTruncate() throws Exception {
        String createdAt = "2023-03-26T20:00:37.000000500Z";
        ProcessKey processKey = new ProcessKey(UUID.randomUUID(), DateTimeUtils.fromIsoString(createdAt));

        Path baseDir = Files.createTempDirectory("testImport");

        writeTempFile(baseDir.resolve("file-1"), "123".getBytes());
        writeTempFile(baseDir.resolve("file-2"), "456".getBytes());

        SecretStoreConfiguration secretCfg = null;
        ProcessConfiguration stateCfg = new ProcessConfiguration(null, Collections.emptyList());
        ProcessKeyCache processKeyCache = null;

        ProcessStateManager stateManager = new ProcessStateManager(getConfiguration(), secretCfg, stateCfg, null, mock(ProcessLogManager.class), processKeyCache);

        // ---
        stateManager.tx(tx -> {
            stateManager.insert(tx, processKey, "_initial/payload.json", "fake-payload".getBytes());
            stateManager.importPath(tx, processKey, "_initial/attachments/", baseDir, (path, basicFileAttributes) -> true);
        });

        stateManager.replace(processKey, ".concord/current_user", "principals".getBytes());

        // ---
        List<String> exported = new ArrayList<>();
        boolean result = stateManager.export(processKey, (name, unixMode, src) -> exported.add(name));
        assertTrue(result);
        // should be 4, but 2 because of createdAt rounded in postgresql JDBC and just truncated in jooq.
        /**
         *  see {@link org.postgresql.jdbc.TimestampUtils#toString(OffsetDateTime)}
         *  and
         *  {@link org.jooq.impl.DefaultBinding.format(OffsetDateTime val, SQLDialect family)
          */
        assertEquals(4, exported.size());
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
