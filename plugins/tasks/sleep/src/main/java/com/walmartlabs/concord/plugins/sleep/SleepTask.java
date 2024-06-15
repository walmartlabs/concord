package com.walmartlabs.concord.plugins.sleep;

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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Named("sleep")
public class SleepTask implements Task {

    private final ApiClientFactory apiClientFactory;

    @Inject
    public SleepTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    @SuppressWarnings("unused")
    public void ms(long t) {
        SleepTaskCommon.sleep(t);
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Supplier<Suspender> suspender = () -> {
            ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder()
                    .sessionToken(ContextUtils.getSessionToken(ctx))
                    .build());

            return new Suspender(apiClient, ContextUtils.getTxId(ctx));
        };

        Map<String, Object> cfg = createCfg(ctx);

        TaskResult taskResult = new SleepTaskCommon(suspender)
                .execute(new TaskParams(cfg));
        if (taskResult instanceof TaskResult.SuspendResult) {
            ctx.suspend(((TaskResult.SuspendResult) taskResult).eventName());
        }
    }

    private static Map<String, Object> createCfg(Context ctx) {
        Map<String, Object> m = new HashMap<>();
        for (String k : Constants.ALL_IN_PARAMS) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }
        return m;
    }
}
