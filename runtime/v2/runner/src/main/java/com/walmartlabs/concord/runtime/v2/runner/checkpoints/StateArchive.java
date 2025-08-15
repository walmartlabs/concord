package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StateArchive implements AutoCloseable {

    private final TemporaryPath dir;

    public StateArchive() throws IOException {
        this.dir = PathUtils.tempDir("state-archive");
    }

    @Override
    public void close() {
        this.dir.close();
    }

    public StateArchive withProcessState(ProcessSnapshot snapshot) {
        try {
            StateManager.saveProcessState(dir.path(), snapshot);
        } catch (IOException e) {
            throw new RuntimeException("Error while saving process state: " + e.getMessage(), e);
        }

        return this;
    }

    public StateArchive withResumeEvent(String name) {
        StateManager.saveResumeEvent(dir.path(), name);
        return this;
    }

    public StateArchive withSystemDirectory(Path workDir) {
        try {
            Path src = workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
            if (Files.notExists(src)) {
                return this;
            }

            Path dst = dir.path().resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
            if (!Files.exists(dst)) {
                Files.createDirectories(dst);
            }

            IOUtils.copy(src, dst);
        } catch (IOException e) {
            throw new RuntimeException("Error while copying the process' system directory: " + e.getMessage(), e);
        }

        return this;
    }

    public TemporaryPath zip() throws IOException {
        Path dst = PathUtils.createTempFile("state", ".zip");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dst));
             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            IOUtils.zip(zip, dir.path());
        }
        return new TemporaryPath(dst);
    }
}
