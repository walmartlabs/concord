package com.walmartlabs.concord.agent.logging;

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
import com.walmartlabs.concord.agent.AgentConstants;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.LogSegmentUpdateRequest;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessLogV2Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.UUID;

public class RemoteLogAppender implements LogAppender {

    private static final Logger log = LoggerFactory.getLogger(RemoteLogAppender.class);

    private final ProcessApi processApi;
    private final ProcessLogV2Api processLogV2Api;

    @Inject
    public RemoteLogAppender(ApiClient apiClient) {
        this.processApi = new ProcessApi(apiClient);
        this.processLogV2Api = new ProcessLogV2Api(apiClient);
    }

    @Override
    public void appendLog(UUID instanceId, byte[] ab) {
        String path = "/api/v1/process/" + instanceId + "/log";

        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                ClientUtils.postData(processApi.getApiClient(), path, ab);
                return null;
            });
        } catch (ApiException e) {
            // TODO handle errors
            log.warn("appendLog ['{}'] -> error: {}", instanceId, e.getMessage());
        }
    }

    @Override
    public boolean appendLog(UUID instanceId, long segmentId, byte[] ab) {
        String path = "/api/v2/process/" + instanceId + "/log/segment/" + segmentId + "/data";

        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                ClientUtils.postData(processApi.getApiClient(), path, ab);
                return null;
            });
            return true;
        } catch (ApiException e) {
            log.warn("appendLog ['{}'] -> error: {}", instanceId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean updateSegment(UUID instanceId, long segmentId, LogSegmentStats stats) {
        LogSegmentUpdateRequest request = new LogSegmentUpdateRequest()
                .setStatus(stats.status())
                .setWarnings(stats.warnings())
                .setErrors(stats.errors());

        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY,
                    () -> processLogV2Api.updateSegment(instanceId, segmentId, request));
            return true;
        } catch (Exception e) {
            log.warn("updateSegment ['{}', '{}', '{}'] -> error: {}", instanceId, segmentId, stats, e.getMessage());
        }
        return false;
    }
}
