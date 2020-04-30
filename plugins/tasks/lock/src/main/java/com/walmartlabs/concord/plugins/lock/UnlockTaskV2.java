package com.walmartlabs.concord.plugins.lock;

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
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.Map;

@Named("unlock")
public class UnlockTaskV2 implements Task {

    private final ApiClient apiClient;
    private final InstanceId processInstanceId;

    @Inject
    public UnlockTaskV2(ApiClient apiClient, InstanceId processInstanceId) {
        this.apiClient = apiClient;
        this.processInstanceId = processInstanceId;
    }

    public Serializable execute(TaskContext ctx) throws ApiException {
        Map<String, Object> input = ctx.input();
        String lockName = LockUtils.getLockName(input);
        String scope = MapUtils.getString(input, LockUtils.SCOPE_KEY, LockUtils.PROJECT_SCOPE);

        LockUtils.unlock(processInstanceId.getValue(), lockName, scope, apiClient);

        return null;
    }
}
