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
import com.walmartlabs.concord.runtime.v2.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;

@Named("unlock")
@DryRunReady
public class UnlockTaskV2 implements Task {

    private final LockTaskCommon delegate;

    @Inject
    public UnlockTaskV2(ApiClient apiClient, Context context) {
        this.delegate = new LockTaskCommon(apiClient, context.processInstanceId());
    }

    public TaskResult execute(Variables input) throws Exception {
        TaskParams params = new TaskParams(input);

        delegate.unlock(params.lockName(), params.scope());

        return TaskResult.success();
    }
}
