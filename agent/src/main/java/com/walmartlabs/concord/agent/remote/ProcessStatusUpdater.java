package com.walmartlabs.concord.agent.remote;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ProcessStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProcessStatusUpdater.class);

    private final String agentId;
    private final ProcessApi processApi;

    public ProcessStatusUpdater(String agentId, ProcessApi processApi) {
        this.agentId = agentId;
        this.processApi = processApi;
    }

    public void update(UUID instanceId, ProcessEntry.StatusEnum status) {
        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                processApi.updateStatus(instanceId, agentId, status.name());
                return null;
            });
        } catch (ApiException e) {
            log.warn("updateStatus ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }
    }
}
