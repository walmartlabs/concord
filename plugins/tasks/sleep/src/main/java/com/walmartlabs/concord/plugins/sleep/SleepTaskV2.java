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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Named("sleep")
public class SleepTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(SleepTaskV2.class);

    private final ApiClient apiClient;
    private final InstanceId processInstanceId;

    @Inject
    public SleepTaskV2(ApiClient apiClient, InstanceId processInstanceId) {
        this.apiClient = apiClient;
        this.processInstanceId = processInstanceId;
    }

    @SuppressWarnings("unused")
    public void ms(long t) {
        SleepTaskUtils.sleep(t);
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        Map<String, Object> cfg = ctx.input();
        Number duration = MapUtils.getNumber(cfg, Constants.DURATION_KEY, null);
        Instant until = getUntil(ctx);

        SleepTaskUtils.validateInputParams(duration, until);

        boolean suspend = MapUtils.getBoolean(cfg, Constants.SUSPEND_KEY, false);
        if (suspend) {
            Instant sleepUntil = SleepTaskUtils.toSleepUntil(duration, until);
            if (sleepUntil.isBefore(Instant.now())) {
                log.warn("Skipping the sleep, the specified datetime is in the " +
                        "past: {}", sleepUntil);
                return null;
            }
            log.info("Sleeping until {}...", sleepUntil);
            suspend(sleepUntil, ctx);
        } else {
            long sleepTime = SleepTaskUtils.toSleepDuration(duration, until);
            if (sleepTime <= 0) {
                log.warn("Skipping the sleep, the specified datetime is either negative " +
                        "or is in the past: {}", sleepTime);
                return null;
            }
            log.info("Sleeping for {}ms", sleepTime);
            SleepTaskUtils.sleep(sleepTime);
        }
        return null;
    }

    private void suspend(Instant until, TaskContext ctx) throws ApiException {
        ProcessApi api = new ProcessApi(apiClient);

        ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () -> {
            api.setWaitCondition(processInstanceId.getValue(), SleepTaskUtils.createCondition(until));
            return null;
        });

        ctx.suspend(Constants.RESUME_EVENT_NAME);
    }

    private static Instant getUntil(TaskContext ctx) {
        Object value = ctx.input().get(Constants.UNTIL_KEY);
        if (value == null) {
            return null;
        }

        return SleepTaskUtils.getUntil(value);
    }
}