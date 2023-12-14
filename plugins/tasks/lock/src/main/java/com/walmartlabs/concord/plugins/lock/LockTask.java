package com.walmartlabs.concord.plugins.lock;

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
import com.walmartlabs.concord.sdk.*;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named("lock")
@SuppressWarnings("unused")
public class LockTask implements Task {

    private final ApiClientFactory apiClientFactory;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @Inject
    public LockTask(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    public void lock(@InjectVariable("txId") String instanceId, String lockName, String scope) throws Exception {
        ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(context))
                .build());

        TaskResult taskResult = new LockTaskCommon(apiClient, UUID.fromString(instanceId))
                .lock(lockName, scope);
        if (taskResult instanceof TaskResult.SuspendResult) {
            context.suspend(((TaskResult.SuspendResult) taskResult).eventName());
        }
    }

    public void unlock(@InjectVariable("txId") String instanceId, String lockName, String scope) throws Exception {
        ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(context))
                .build());

        new LockTaskCommon(apiClient, UUID.fromString(instanceId))
                .unlock(lockName, scope);
    }
}
