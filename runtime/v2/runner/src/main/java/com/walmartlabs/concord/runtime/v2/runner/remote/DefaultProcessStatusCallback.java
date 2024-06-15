package com.walmartlabs.concord.runtime.v2.runner.remote;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.ProcessStatusCallback;

import javax.inject.Inject;
import java.util.UUID;

public class DefaultProcessStatusCallback implements ProcessStatusCallback {

    private final RunnerConfiguration cfg;
    private final ProcessApi processApi;

    @Inject
    public DefaultProcessStatusCallback(RunnerConfiguration cfg, ApiClient apiClient) {
        this.cfg = cfg;
        this.processApi = new ProcessApi(apiClient);
    }

    @Override
    public void onRunning(UUID instanceId) {
        try {
            ClientUtils.withRetry(cfg.api().retryCount(), cfg.api().retryInterval(), () -> {
                processApi.updateStatus(instanceId, cfg.agentId(), ProcessEntry.StatusEnum.RUNNING.toString());
                return null;
            });
        } catch (ApiException e) {
            throw new RuntimeException("Error while updating the process status: " + e.getMessage(), e);
        }
    }
}
