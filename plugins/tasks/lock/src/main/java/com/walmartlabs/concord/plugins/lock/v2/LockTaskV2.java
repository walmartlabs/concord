package com.walmartlabs.concord.plugins.lock.v2;

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
import com.walmartlabs.concord.plugins.lock.LockTaskCommon;
import com.walmartlabs.concord.plugins.lock.TaskParams;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;

@Named("lock")
@SuppressWarnings("unused")
public class LockTaskV2 implements Task {

    private final LockTaskCommon delegate;
    private final Context context;

    @Inject
    public LockTaskV2(ApiClient apiClient, Context context) {
        this.delegate = new LockTaskCommon(apiClient, context.processInstanceId());
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        TaskParams params = new TaskParams(input);

        return delegate.lock(params.lockName(), params.scope());
    }

    public void lock(String lockName, String scope) throws Exception {
        TaskResult taskResult = delegate.lock(lockName, scope);
        if (taskResult instanceof TaskResult.SuspendResult) {
            context.suspend(((TaskResult.SuspendResult) taskResult).eventName());
        }
    }

    public void unlock(String lockName, String scope) throws Exception {
        delegate.unlock(lockName, scope);
    }
}
