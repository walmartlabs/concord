package com.walmartlabs.concord.server.process.state;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Ignore;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

@Ignore
public class ProcessStateManagerTest extends AbstractDaoTest {

    @Test
    public void testLargeImport() throws Exception {
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

        ProcessStateManager stateManager = new ProcessStateManager(getConfiguration());
        stateManager.transaction(tx -> {
            stateManager.importPath(tx, UUID.randomUUID(), "/", baseDir);
        });
    }
}
