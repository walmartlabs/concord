package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.eclipse.sisu.Priority;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Function;

@Priority(-1)
@Singleton
public class MockTaskProvider implements TaskProvider {

    private final MockDefinitionProvider mockDefinitionProvider;
    private final Provider<List<TaskProvider>> taskProviderProvider;

    @Inject
    public MockTaskProvider(MockDefinitionProvider mockDefinitionProvider, Provider<List<TaskProvider>> taskProviderProvider) {
        this.mockDefinitionProvider = mockDefinitionProvider;
        this.taskProviderProvider = taskProviderProvider;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        boolean mocked = mockDefinitionProvider.isTaskMocked(ctx, key);
        if (mocked) {
            return new MockTask(ctx, key, mockDefinitionProvider, originTaskClass(ctx, key), () -> originalTask(ctx, key));
        }

        return null;
    }

    @Override
    public Class<? extends Task> getTaskClass(Context ctx, String key) {
        return null;
    }

    @Override
    public boolean hasTask(String key) {
        return false;
    }

    @Override
    public Set<String> names() {
        return Collections.emptySet();
    }

    private Class<? extends Task> originTaskClass(Context ctx, String key) {
        return findFirstMatchingTaskProvider((provider) -> provider.getTaskClass(ctx, key))
                .orElseThrow(() -> new UserDefinedException("Task not found: '" + key + "'"));
    }

    private Task originalTask(Context ctx, String key) {
        return findFirstMatchingTaskProvider((provider) -> provider.createTask(ctx, key))
                .orElseThrow(() -> new UserDefinedException("Task not found: '" + key + "'"));
    }

    private <T> Optional<T> findFirstMatchingTaskProvider(Function<TaskProvider, T> taskFunction) {
        return taskProviderProvider.get().stream()
                .filter(MockTaskProvider::isNotMockProvider)
                .sorted(Comparator.comparingInt(MockTaskProvider::getPriority))
                .map(taskFunction)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static int getPriority(TaskProvider p) {
        Class<? extends TaskProvider> klass = p.getClass();
        Priority priority = klass.getDeclaredAnnotation(Priority.class);
        if (priority == null) {
            return 0;
        }
        return priority.value();
    }

    private static boolean isNotMockProvider(TaskProvider provider) {
        return !(provider instanceof MockTaskProvider);
    }
}
