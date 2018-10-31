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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.ProcessOutVariables;
import com.walmartlabs.concord.server.process.checkpoint.ProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.archive.ProcessCheckpointArchiver;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.Constants.Files.CHECKPOINT_META_FILE_NAME;

@Named
public class ProcessCheckpointManager {

    private final ProcessCheckpointArchiver archiver;
    private final CheckpointDao checkpointDao;
    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;

    @Inject
    protected ProcessCheckpointManager(ProcessCheckpointArchiver archiver,
                                       CheckpointDao checkpointDao,
                                       ProcessQueueDao queueDao,
                                       ProcessStateManager stateManager) {

        this.archiver = archiver;
        this.checkpointDao = checkpointDao;
        this.queueDao = queueDao;
        this.stateManager = stateManager;
    }

    /**
     * Import checkpoints data from the specified directory or a file.
     *
     * @param instanceId     process instance ID
     * @param checkpointId   process checkpoint ID
     * @param checkpointName process checkpoint name
     * @param data           checkpoint data file
     */
    public void importCheckpoint(UUID instanceId, UUID checkpointId, String checkpointName, Path data) {
        checkpointDao.importCheckpoint(instanceId, checkpointId, checkpointName, data);
    }

    /**
     * Restore process to a saved checkpoint.
     *
     * @param instanceId
     * @param checkpointId
     * @return
     */
    public String restoreCheckpoint(UUID instanceId, UUID checkpointId) {
        try (TemporaryPath checkpointArchive = IOUtils.tempFile("checkpoint", ".zip")) {

            boolean hasCheckpoint = export(checkpointId, checkpointArchive.path());
            if (!hasCheckpoint) {
                return null;
            }

            try (TemporaryPath extractedDir = IOUtils.tempDir("unzipped-checkpoint")) {
                IOUtils.unzip(checkpointArchive.path(), extractedDir.path());

                String checkpointName = readCheckpointName(extractedDir.path());

                stateManager.deleteDirectory(instanceId, InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME);
                stateManager.deleteDirectory(instanceId, InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME);
                stateManager.importPath(instanceId, null, extractedDir.path());

                Map<String, Object> out = ProcessOutVariables.read(extractedDir.path().resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME));
                if (out.isEmpty()) {
                    queueDao.removeMeta(instanceId, "out");
                } else {
                    queueDao.updateMeta(instanceId, Collections.singletonMap("out", out));
                }

                return checkpointName;
            }
        } catch (Exception e) {
            throw new RuntimeException("Restore checkpoint '" + checkpointId + "' error", e);
        }
    }

    /**
     * List checkpoints of a given instanceId
     *
     * @param instanceId process instance ID
     * @return {@link ProcessCheckpointEntry}
     */
    public List<ProcessCheckpointEntry> list(UUID instanceId) {
        return checkpointDao.list(instanceId);
    }

    private String readCheckpointName(Path checkpointDir) throws IOException {
        Path checkpoint = checkpointDir.resolve(CHECKPOINT_META_FILE_NAME);
        if (!Files.exists(checkpoint)) {
            throw new RuntimeException("Invalid checkpoint archive: " + checkpointDir);
        }
        String checkpointName = new String(Files.readAllBytes(checkpoint));
        Files.delete(checkpoint);
        return checkpointName;
    }

    private boolean export(UUID checkpointId, Path dest) throws IOException {
        if (archiver.isArchived(checkpointId)) {
            return archiver.export(checkpointId, dest);
        } else {
            return checkpointDao.export(checkpointId, dest);
        }
    }
}
