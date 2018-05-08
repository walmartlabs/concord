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

import com.squareup.okhttp.Call;
import com.walmartlabs.concord.server.ApiClient;
import com.walmartlabs.concord.server.ApiException;
import com.walmartlabs.concord.server.Pair;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.client.ClientUtils;
import com.walmartlabs.concord.server.client.ProcessApi;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    public void updateStatus(UUID instanceId, ProcessStatus status) throws ApiException {
        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            processApi.updateStatus(instanceId, agentId, status.name());
            return null;
        });
    }

    public void appendLog(UUID instanceId, byte[] data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/log";

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            postData(path, data);
            return null;
        });
    }

    public void uploadAttachments(UUID instanceId, Path data) throws ApiException {
        String path = "/api/v1/process/" + instanceId + "/attachment";

        ClientUtils.withRetry(retryCount, retryInterval, () -> {
            postData(path, data.toFile());
            return null;
        });
    }

    private void postData(String path, Object data) throws ApiException {
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Content-Type", MediaType.APPLICATION_OCTET_STREAM);

        String[] authNames = new String[] { "api_key" };

        ApiClient client = processApi.getApiClient();
        Call c = processApi.getApiClient().buildCall(path, "POST", new ArrayList<>(), new ArrayList<>(),
                data, headerParams, new HashMap<>(), authNames, null);
        client.execute(c);
    }
}
