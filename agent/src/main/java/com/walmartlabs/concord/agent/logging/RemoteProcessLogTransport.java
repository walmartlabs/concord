package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.LogSegmentUpdateRequest;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessLogV2Api;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.UUID;

public class RemoteProcessLogTransport implements ProcessLogTransport {

    private static final Logger log = LoggerFactory.getLogger(RemoteProcessLogTransport.class);

    private final UUID instanceId;
    private final ProcessApi processApi;
    private final ProcessLogV2Api processLogV2Api;

    public RemoteProcessLogTransport(UUID instanceId, ApiClient apiClient) {
        this(instanceId, new ProcessApi(apiClient), new ProcessLogV2Api(apiClient));
    }

    RemoteProcessLogTransport(UUID instanceId, ProcessApi processApi, ProcessLogV2Api processLogV2Api) {
        this.instanceId = instanceId;
        this.processApi = processApi;
        this.processLogV2Api = processLogV2Api;
    }

    @Override
    public DeliveryStatus appendSystem(byte[] bytes) {
        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                processApi.appendProcessLog(instanceId, new ByteArrayInputStream(bytes));
                return null;
            });
            return DeliveryStatus.DELIVERED;
        } catch (ApiException e) {
            log.warn("appendSystem ['{}'] -> error: {}", instanceId, e.getMessage());
            return DeliveryStatus.FAILED;
        }
    }

    @Override
    public DeliveryStatus appendSegment(long segmentId, byte[] bytes) {
        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                processLogV2Api.appendProcessLogSegment(instanceId, segmentId, new ByteArrayInputStream(bytes));
                return null;
            });
            return DeliveryStatus.DELIVERED;
        } catch (ApiException e) {
            log.warn("appendSegment ['{}', '{}'] -> error: {}", instanceId, segmentId, e.getMessage());
            return DeliveryStatus.FAILED;
        }
    }

    @Override
    public DeliveryStatus updateSegment(long segmentId, LogSegmentStats stats) {
        var request = new LogSegmentUpdateRequest()
                .status(convertStatus(stats.status()))
                .warnings(stats.warnings())
                .errors(stats.errors());

        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY,
                    () -> processLogV2Api.updateProcessLogSegment(instanceId, segmentId, request));
            return DeliveryStatus.DELIVERED;
        } catch (Exception e) {
            log.warn("updateSegment ['{}', '{}', '{}'] -> error: {}", instanceId, segmentId, stats, e.getMessage());
            return DeliveryStatus.FAILED;
        }
    }

    private static LogSegmentUpdateRequest.StatusEnum convertStatus(LogSegmentStatus status) {
        if (status == null) {
            return null;
        }

        return switch (status) {
            case ERROR -> LogSegmentUpdateRequest.StatusEnum.FAILED;
            case OK -> LogSegmentUpdateRequest.StatusEnum.OK;
            case RUNNING -> LogSegmentUpdateRequest.StatusEnum.RUNNING;
            case SUSPENDED -> LogSegmentUpdateRequest.StatusEnum.SUSPENDED;
        };
    }
}
