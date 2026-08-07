package com.walmartlabs.concord.runtime.v25.runner.task;

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
import com.walmartlabs.concord.runtime.v2.sdk.TaskProvider;
import org.eclipse.sisu.Priority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TaskRegistry {

    private final List<TaskProvider> providers;
    private final Map<String, List<TaskProvider>> providersByName;
    private final Set<String> names;

    public TaskRegistry(Collection<? extends TaskProvider> providers) {
        var ordered = new ArrayList<>(providers);
        ordered.sort(Comparator.comparingInt(TaskRegistry::priority)
                .thenComparing(provider -> provider.getClass().getName()));
        this.providers = Collections.unmodifiableList(ordered);

        var byName = new LinkedHashMap<String, List<TaskProvider>>();
        for (var provider : ordered) {
            var exportedNames = new ArrayList<>(provider.names());
            exportedNames.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
            for (var name : exportedNames) {
                byName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(provider);
            }
        }
        byName.replaceAll((name, entries) -> List.copyOf(entries));
        this.providersByName = Collections.unmodifiableMap(byName);
        this.names = Collections.unmodifiableSet(new LinkedHashSet<>(providersByName.keySet()));
    }

    public boolean hasTask(String name) {
        if (providersByName.containsKey(name)) {
            return true;
        }
        return providers.stream().anyMatch(provider -> provider.hasTask(name));
    }

    public Task create(Context context, String name) {
        return resolve(context, name).task();
    }

    public Class<? extends Task> taskClass(Context context, String name) {
        return resolve(context, name).taskClass();
    }

    public ResolvedTask resolve(Context context, String name) {
        for (var provider : providers) {
            var task = provider.createTask(context, name);
            if (task == null) {
                continue;
            }
            var taskClass = taskClass(task);
            for (var candidate : providers) {
                var candidateClass = candidate.getTaskClass(context, name);
                if (candidateClass != null) {
                    taskClass = candidateClass;
                    break;
                }
            }
            return new ResolvedTask(task, taskClass);
        }
        throw taskNotFound(name);
    }

    public Set<String> names() {
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Task> taskClass(Task task) {
        return (Class<? extends Task>) task.getClass();
    }

    private static IllegalArgumentException taskNotFound(String name) {
        return new IllegalArgumentException("Task not found: '" + name + "'");
    }

    private static int priority(TaskProvider provider) {
        var priority = provider.getClass().getDeclaredAnnotation(Priority.class);
        return priority != null ? priority.value() : 0;
    }

    public record ResolvedTask(Task task, Class<? extends Task> taskClass) {
    }
}
