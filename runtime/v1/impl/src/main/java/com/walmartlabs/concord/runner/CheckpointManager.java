package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.api.ExecutionException;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final UUID instanceId;

    private final ProcessApiClient processApiClient;

    public CheckpointManager(UUID instanceId, ProcessApiClient processApiClient) {
        this.instanceId = instanceId;
        this.processApiClient = processApiClient;
    }

    public void process(UUID checkpointId, UUID correlationId, String checkpointName, Path baseDir) throws ExecutionException {
        try {
            Path checkpointDir = baseDir.resolve(Constants.Files.JOB_CHECKPOINTS_DIR_NAME);
            if (!Files.exists(checkpointDir)) {
                Files.createDirectories(checkpointDir);
            }

            Path checkpointMeta = baseDir.resolve(Constants.Files.CHECKPOINT_META_FILE_NAME);
            Files.write(checkpointMeta, checkpointName.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try (TemporaryPath checkpointFile = new TemporaryPath(checkpointDir.resolve(checkpointId + "_" + checkpointName + ".zip"))) {
                try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(checkpointFile.path()))) {
                    ZipUtils.zip(zip, InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME + "/", baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME));
                    ZipUtils.zip(zip, InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME + "/", baseDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME));
                    ZipUtils.zipFile(zip, checkpointMeta, Constants.Files.CHECKPOINT_META_FILE_NAME);
                }

                try (InputStream in = Files.newInputStream(checkpointFile.path())) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", checkpointId);
                    data.put("correlationId", correlationId);
                    data.put("name", checkpointName);
                    data.put("data", in);

                    processApiClient.uploadCheckpoint(instanceId, data);
                }
            }

            Files.delete(checkpointMeta);

            // write resume event
            Path resumeEventFile = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME)
                    .resolve(InternalConstants.Files.RESUME_MARKER_FILE_NAME);

            Files.write(resumeEventFile, checkpointName.getBytes());

            log.info("checkpoint {} ('{}')", checkpointName, checkpointId);
        } catch (Exception e) {
            throw new ExecutionException("Checkpoint process error", e);
        }
    }
}
