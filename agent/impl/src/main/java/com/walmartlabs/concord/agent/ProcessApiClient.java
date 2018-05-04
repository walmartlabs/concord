package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.ApiException;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.client.ProcessApi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class ProcessApiClient extends AbstractClient {

    private final String agentId;
    private final ProcessApi processApi;

    public ProcessApiClient(Configuration cfg) throws IOException {
        super(cfg);
        this.agentId = cfg.getAgentId();
        this.processApi = new ProcessApi(getClient());
    }

    public void updateStatus(UUID instanceId, ProcessStatus status) throws ApiException {
        withRetry(() -> {
            processApi.updateStatus(instanceId, agentId, status.name());
            return null;
        });
    }

    public void appendLog(UUID instanceId, byte[] data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/log";

        postData(path, data);
    }

    public void uploadAttachments(UUID instanceId, Path data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/attachment";
        postData(path, data.toFile());
    }
}
