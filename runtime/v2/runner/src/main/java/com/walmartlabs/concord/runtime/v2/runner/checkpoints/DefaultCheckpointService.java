package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.runner.ProcessSnapshot;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultCheckpointService implements CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCheckpointService.class);

    private final InstanceId instanceId;
    private final WorkingDirectory workingDirectory;
    private final ApiClient apiClient;
    private final ApiConfiguration apiConfiguration;

    @Inject
    public DefaultCheckpointService(InstanceId instanceId, WorkingDirectory workingDirectory, RunnerConfiguration configuration, ApiClient apiClient) {
        this.instanceId = instanceId;
        this.workingDirectory = workingDirectory;
        this.apiConfiguration = configuration.api();
        this.apiClient = apiClient;
    }

    @Override
    public void create(String name, Runtime runtime, ProcessSnapshot snapshot) {
        UUID checkpointId = UUID.randomUUID();

        Path checkpointArchive = null;
        try {
            checkpointArchive = archiveState(checkpointId, name, snapshot);

            Map<String, Object> data = new HashMap<>();
            data.put("id", checkpointId);
            data.put("name", name);
            data.put("data", checkpointArchive);

            uploadCheckpoint(instanceId.getValue(), data);
        } catch (Exception e) {
            throw new RuntimeException("Checkpoint upload error", e);
        } finally {
            if (checkpointArchive != null) {
                try {
                    Files.deleteIfExists(checkpointArchive);
                } catch (IOException e) {
                    log.warn("create ['{}'] -> cleanup error", name);
                }
            }
        }

        log.info("create ['{}'] -> done", name);
    }

    private Path archiveState(UUID checkpointId, String checkpointName, ProcessSnapshot snapshot) throws IOException {
        Path checkpointDir = workingDirectory.getValue().resolve(Constants.Files.JOB_CHECKPOINTS_DIR_NAME);
        if (!Files.exists(checkpointDir)) {
            Files.createDirectories(checkpointDir);
        }

        Path result = checkpointDir.resolve(checkpointId + "_" + checkpointName + ".zip");
        StateManager.archive(workingDirectory.getValue(), snapshot, result);
        return result;
    }

    private void uploadCheckpoint(UUID instanceId, Map<String, Object> data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/checkpoint";

        ClientUtils.withRetry(apiConfiguration.retryCount(), apiConfiguration.retryInterval(), () -> {
            ClientUtils.postData(apiClient, path, data, null);
            return null;
        });
    }
}
