package com.walmartlabs.concord.plugins.sleep;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ClientUtils;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Suspender {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.
            ofPattern(Constants.DATETIME_PATTERN).withZone(ZoneOffset.UTC);

    private final ProcessApi api;
    private final UUID instanceId;

    public Suspender(ApiClient api, UUID instanceId) {
        this.api = new ProcessApi(api);
        this.instanceId = instanceId;
    }

    public TaskResult suspend(Instant until) throws ApiException {
        String eventName = UUID.randomUUID().toString();

        ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () -> {
            api.setWaitCondition(instanceId, createCondition(until, eventName));
            return null;
        });

        return TaskResult.suspend(eventName);
    }

    private static Map<String, Object> createCondition(Instant until, String eventName) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_SLEEP");
        condition.put("until", dateFormatter.format(until));
        condition.put("reason", "Waiting till " + until);
        condition.put("resumeEvent", eventName);
        return condition;
    }
}
