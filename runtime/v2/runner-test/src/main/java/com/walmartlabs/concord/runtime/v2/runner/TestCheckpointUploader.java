package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointUploader;
import com.walmartlabs.concord.sdk.Constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestCheckpointUploader implements CheckpointUploader {

    private final Map<String, Path> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void upload(UUID checkpointId, UUID correlationId, String name, Path archivePath) throws Exception {
        Path tmpFile = PathUtils.createTempDir("unittests").resolve(archivePath.getFileName());
        Files.move(archivePath, tmpFile);
        checkpoints.put(name, tmpFile);

        System.out.println(checkpoints);
    }

    public void put(String name, Path archivePath) {
        checkpoints.put(name, archivePath);
    }

    public void restore(String name, Path workDir) throws Exception {
        Path archive = checkpoints.get(name);
        assertNotNull(archive);

        PathUtils.deleteRecursively(workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME));
        PathUtils.deleteRecursively(workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));

        ZipUtils.unzip(archive, workDir);
    }
}
