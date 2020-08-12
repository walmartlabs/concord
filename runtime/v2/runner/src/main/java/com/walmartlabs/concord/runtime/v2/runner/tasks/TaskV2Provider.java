package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.runner.DefaultTaskVariablesService;
import com.walmartlabs.concord.runtime.v2.runner.context.TaskContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

public class TaskV2Provider implements TaskProvider {

    private final Injector injector;
    private final TaskHolder<Task> holder;
    private final DefaultTaskVariablesService defaultTaskVariables;

    @Inject
    public TaskV2Provider(Injector injector,
                          TaskHolder<Task> holder,
                          DefaultTaskVariablesService defaultTaskVariables) {

        this.injector = injector;
        this.holder = holder;
        this.defaultTaskVariables = defaultTaskVariables;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        Class<? extends Task> klass = holder.get(key);
        if (klass == null) {
            return null;
        }

        Map<String, Object> defaultVariables = defaultTaskVariables.get(key);
        TaskContext taskContext = new TaskContext(ctx, new MapBackedVariables(defaultVariables));
        return ContextProvider.withContext(taskContext, () -> injector.getInstance(klass));
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
