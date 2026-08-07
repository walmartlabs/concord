package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;

@Named("v25ProcessConfiguration")
public class V25ProcessConfigurationTask implements Task {

    private final Context context;

    @Inject
    public V25ProcessConfigurationTask(Context context) {
        this.context = context;
    }

    @Override
    public TaskResult execute(Variables input) {
        var configuration = context.processConfiguration();
        return TaskResult.success()
                .value("meta", configuration.meta().get("source"))
                .value("events", configuration.events().recordEvents())
                .value("out", configuration.out().get(0));
    }
}
