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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.OutVariablesUtils;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.apache.shiro.authz.UnauthorizedException;

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

    private final ProcessCheckpointDao checkpointDao;
    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    protected ProcessCheckpointManager(ProcessCheckpointDao checkpointDao,
                                       ProcessQueueDao queueDao,
                                       ProcessStateManager stateManager,
                                       ProjectAccessManager projectAccessManager) {

        this.checkpointDao = checkpointDao;
        this.queueDao = queueDao;
        this.stateManager = stateManager;
        this.projectAccessManager = projectAccessManager;
    }

    public UUID getRecentCheckpointId(ProcessKey processKey, String checkpointName) {
        return checkpointDao.getRecentId(processKey, checkpointName);
    }

    /**
     * Import checkpoints data from the specified directory or a file.
     *
     * @param processKey     process key
     * @param checkpointId   process checkpoint ID
     * @param checkpointName process checkpoint name
     * @param data           checkpoint data file
     */
    public void importCheckpoint(ProcessKey processKey, UUID checkpointId, String checkpointName, Path data) {
        checkpointDao.importCheckpoint(processKey, checkpointId, checkpointName, data);
    }

    /**
     * Restore process to a saved checkpoint.
     */
    public String restoreCheckpoint(ProcessKey processKey, UUID checkpointId) {
        try (TemporaryPath checkpointArchive = IOUtils.tempFile("checkpoint", ".zip")) {

            boolean hasCheckpoint = export(processKey, checkpointId, checkpointArchive.path());
            if (!hasCheckpoint) {
                return null;
            }

            try (TemporaryPath extractedDir = IOUtils.tempDir("unzipped-checkpoint")) {
                IOUtils.unzip(checkpointArchive.path(), extractedDir.path());

                String checkpointName = readCheckpointName(extractedDir.path());

                stateManager.deleteDirectory(processKey, Constants.Files.CONCORD_SYSTEM_DIR_NAME);
                stateManager.deleteDirectory(processKey, Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
                stateManager.importPath(processKey, null, extractedDir.path());

                Map<String, Object> out = OutVariablesUtils.read(extractedDir.path().resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));
                if (out.isEmpty()) {
                    queueDao.removeMeta(processKey, "out");
                } else {
                    queueDao.updateMeta(processKey, Collections.singletonMap("out", out));
                }

                return checkpointName;
            }
        } catch (Exception e) {
            throw new RuntimeException("Restore checkpoint '" + checkpointId + "' error", e);
        }
    }

    /**
     * List checkpoints of a given instanceId
     */
    public List<ProcessCheckpointEntry> list(ProcessKey processKey) {
        return checkpointDao.list(processKey);
    }

    public void assertProcessAccess(ProcessEntry e) {
        UserPrincipal p = UserPrincipal.assertCurrent();

        UUID initiatorId = e.initiatorId();
        if (p.getId().equals(initiatorId)) {
            // process owners should be able to restore the process from a checkpoint
            return;
        }

        if (Roles.isAdmin()) {
            return;
        }

        UUID projectId = e.projectId();
        if (projectId != null) {
            projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, false);
            return;
        }

        throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                "the necessary permissions to restore the process using a checkpoint: " + e.instanceId());
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

    private boolean export(ProcessKey processKey, UUID checkpointId, Path dest) throws IOException {
        return checkpointDao.export(processKey, checkpointId, dest);
    }
}
