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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;

import java.util.Map;
import java.util.UUID;

public class ProcessApiClient {

    private final ApiClient apiClient;
    private final ProcessApi processApi;

    private final int retryCount;
    private final long retryInterval;

    public ProcessApiClient(RunnerConfiguration runnerCfg, ApiClient apiClient) {
        this.apiClient = apiClient;
        this.processApi = new ProcessApi(apiClient);

        this.retryCount = runnerCfg.api().retryCount();
        this.retryInterval = runnerCfg.api().retryInterval();
    }

    public void uploadCheckpoint(UUID instanceId, Map<String, Object> data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/checkpoint";

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            ClientUtils.postData(apiClient, path, data, null);
            return null;
        });
    }

    public void updateStatus(UUID instanceId, String agentId, ProcessEntry.StatusEnum status) throws ApiException {
        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            processApi.updateStatus(instanceId, agentId, status.name());
            return null;
        });
    }
}
