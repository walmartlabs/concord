package com.walmartlabs.concord.runtime.v2.runner.logging;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.LogSegmentOperationResponse;
import com.walmartlabs.concord.client.LogSegmentRequest;
import com.walmartlabs.concord.client.ProcessLogV2Api;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Singleton
public class DefaultLoggingClient implements LoggingClient {

    private final ProcessLogV2Api api;
    private final UUID instanceId;
    private final RunnerConfiguration cfg;

    @Inject
    public DefaultLoggingClient(ApiClient apiClient, ProcessConfiguration processCfg, RunnerConfiguration cfg) {
        this.api = new ProcessLogV2Api(apiClient);
        this.instanceId = processCfg.instanceId();
        this.cfg = cfg;
    }

    public long createSegment(UUID correlationId, String name) {
        LogSegmentRequest request = new LogSegmentRequest()
                .setCorrelationId(correlationId)
                .setCreatedAt(OffsetDateTime.now(ZoneId.of("UTC")))
                .setName(name);

        try {
            LogSegmentOperationResponse result = ClientUtils.withRetry(cfg.api().retryCount(), cfg.api().retryInterval(), () -> api.segment(instanceId, request));
            return result.getId();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }
}
