package com.walmartlabs.concord.agent.logging;

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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProcessLogTest {

    @TempDir
    Path logDir;

    @Test
    void deleteRemovesTheLogFileAndItsProcessDirectory() throws IOException {
        Path processDir = newProcessDir();
        LocalProcessLog processLog = new LocalProcessLog(processDir);
        assertTrue(Files.exists(processLog.logFile()));

        processLog.delete();

        assertFalse(Files.exists(processLog.logFile()));
        assertFalse(Files.exists(processDir), "the per-process directory must not be left behind");
        assertTrue(Files.exists(logDir), "the shared log directory must stay");
    }

    @Test
    void deleteKeepsTheProcessDirectoryIfSomethingElseIsInIt() throws IOException {
        Path processDir = newProcessDir();
        LocalProcessLog processLog = new LocalProcessLog(processDir);
        Path other = Files.createFile(processDir.resolve("other.txt"));

        assertDoesNotThrow(processLog::delete);

        assertFalse(Files.exists(processLog.logFile()));
        assertTrue(Files.exists(other), "files that are not ours must not be removed");
    }

    @Test
    void deleteCanBeCalledTwice() throws IOException {
        LocalProcessLog processLog = new LocalProcessLog(newProcessDir());

        processLog.delete();

        assertDoesNotThrow(processLog::delete);
    }

    // ProcessLogFactory creates one directory per process before creating the log
    private Path newProcessDir() throws IOException {
        return Files.createDirectory(logDir.resolve(UUID.randomUUID().toString()));
    }
}
