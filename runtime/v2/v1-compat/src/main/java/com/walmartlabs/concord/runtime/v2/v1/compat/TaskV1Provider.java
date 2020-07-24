package com.walmartlabs.concord.runtime.v2.v1.compat;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import org.eclipse.sisu.Priority;

import javax.inject.Inject;
import java.util.Set;

@Priority(100)
public class TaskV1Provider implements TaskProvider {

    private final Injector injector;
    private final TaskHolder<com.walmartlabs.concord.sdk.Task> holder;
    private final WorkingDirectory workingDirectory;

    @Inject
    public TaskV1Provider(Injector injector,
                          @V1 TaskHolder<com.walmartlabs.concord.sdk.Task> holder,
                          WorkingDirectory workingDirectory) {

        this.injector = injector;
        this.holder = holder;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        Class<? extends com.walmartlabs.concord.sdk.Task> klassV1 = holder.get(key);
        if (klassV1 == null) {
            return null;
        }

        com.walmartlabs.concord.sdk.Task v1Task = injector.getInstance(klassV1);
        return new TaskV1Wrapper(ctx, v1Task, workingDirectory.getValue());
    }

    @Override
    public boolean hasTask(String key) {
        return holder.get(key) != null;
    }

    @Override
    public Set<String> names() {
        return holder.keys();
    }
}
