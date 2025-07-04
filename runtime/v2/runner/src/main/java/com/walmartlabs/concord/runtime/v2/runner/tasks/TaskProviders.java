package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import org.eclipse.sisu.Priority;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Container for all registered {@link TaskProvider} instances.
 */
@Named
@Singleton
public class TaskProviders {

    private final List<TaskProvider> taskProviders;

    public TaskProviders() {
        this(Collections.emptySet());
    }

    @Inject
    public TaskProviders(Set<TaskProvider> taskProviders) {
        this.taskProviders = taskProviders.stream()
                .sorted(Comparator.comparingInt(TaskProviders::getPriority))
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
    }

    public Task createTask(Context ctx, String key) {
        for (TaskProvider p : taskProviders) {
            Task t = p.createTask(ctx, key);
            if (t != null) {
                return t;
            }
        }

        return null;
    }

    public boolean hasTask(String key) {
        for (TaskProvider p : taskProviders) {
            if (p.hasTask(key)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> names() {
        Set<String> result = new HashSet<>();
        for (TaskProvider p : taskProviders) {
            result.addAll(p.names());
        }
        return result;
    }

    private static int getPriority(TaskProvider p) {
        Class<? extends TaskProvider> klass = p.getClass();
        Priority priority = klass.getDeclaredAnnotation(Priority.class);
        if (priority == null) {
            return 0;
        }
        return priority.value();
    }
}

