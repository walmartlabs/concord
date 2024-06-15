package com.walmartlabs.concord.plugins.sleep.v2;

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
import com.walmartlabs.concord.plugins.sleep.SleepTaskCommon;
import com.walmartlabs.concord.plugins.sleep.Suspender;
import com.walmartlabs.concord.plugins.sleep.TaskParams;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;

@Named("sleep")
public class SleepTaskV2 implements Task {

    private final SleepTaskCommon delegate;

    @Inject
    public SleepTaskV2(ApiClient apiClient, Context context) {
        this.delegate = new SleepTaskCommon(() -> new Suspender(apiClient, context.processInstanceId()));
    }

    @SuppressWarnings("unused")
    public void ms(long t) {
        SleepTaskCommon.sleep(t);
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        return delegate.execute(new TaskParams(input));
    }
}