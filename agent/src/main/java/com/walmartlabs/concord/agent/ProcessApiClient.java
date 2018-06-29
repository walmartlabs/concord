package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;

import java.nio.file.Path;
import java.util.UUID;

public class ProcessApiClient {

    private final String agentId;
    private final ProcessApi processApi;

    private final int retryCount;
    private final long retryInterval;

    public ProcessApiClient(Configuration cfg, ProcessApi processApi) {
        this.agentId = cfg.getAgentId();
        this.processApi = processApi;

        this.retryCount = cfg.getRetryCount();
        this.retryInterval = cfg.getRetryInterval();
    }

    public void updateStatus(UUID instanceId, ProcessEntry.StatusEnum status) throws ApiException {
        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            processApi.updateStatus(instanceId, agentId, status.name());
            return null;
        });
    }

    public void appendLog(UUID instanceId, byte[] data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/log";

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            ClientUtils.postData(processApi.getApiClient(), path, data);
            return null;
        });
    }

    public void uploadAttachments(UUID instanceId, Path data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/attachment";

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            ClientUtils.postData(processApi.getApiClient(), path, data.toFile());
            return null;
        });
    }
}
