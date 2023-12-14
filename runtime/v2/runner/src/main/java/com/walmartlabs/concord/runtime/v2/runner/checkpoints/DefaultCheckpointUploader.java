package com.walmartlabs.concord.runtime.v2.runner.checkpoints;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultCheckpointUploader implements CheckpointUploader {

    private final InstanceId instanceId;
    private final ApiClient apiClient;
    private final ApiConfiguration apiConfiguration;

    @Inject
    public DefaultCheckpointUploader(InstanceId instanceId,
                                     RunnerConfiguration configuration,
                                     ApiClient apiClient) {
        this.instanceId = instanceId;
        this.apiClient = apiClient;
        this.apiConfiguration = configuration.api();
    }

    @Override
    public void upload(UUID checkpointId, UUID correlationId, String name, Path path) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("id", checkpointId);
        data.put("correlationId", correlationId);
        data.put("name", name);
        data.put("data", path);

        ClientUtils.withRetry(apiConfiguration.retryCount(), apiConfiguration.retryInterval(), () -> {
            new CheckpointApi(apiClient).uploadCheckpoint(instanceId.getValue(), data);
            return null;
        });
    }
}