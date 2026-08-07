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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRegistryTest {

    @Test
    void honorsProviderPriorityAndFallsThroughNullResults() {
        var registry = new TaskRegistry(List.of(new LaterProvider(), new EarlierProvider()));

        assertSame(EarlierTask.class, registry.taskClass(null, "shared"));
        assertSame(EarlierTask.class, registry.create(null, "shared").getClass());
        assertSame(EarlierTask.class, registry.taskClass(null, "dynamic"));
        assertSame(EarlierTask.class, registry.create(null, "dynamic").getClass());
        assertTrue(registry.hasTask("dynamic"));
        assertSame(LaterTask.class, registry.taskClass(null, "fallback"));
        assertEquals(Set.of("shared", "fallback"), registry.names());
    }

    @Test
    void resolvesDuplicateExportedTaskNamesByProviderPriority() {
        var registry = new TaskRegistry(List.of(new DuplicateLaterProvider(), new DuplicateEarlierProvider()));

        assertSame(EarlierTask.class, registry.taskClass(null, "duplicate"));
        assertSame(EarlierTask.class, registry.create(null, "duplicate").getClass());
    }

    @Test
    void fallsBackToTheCreatedTaskClassWhenProviderMetadataIsUnavailable() {
        var registry = new TaskRegistry(List.of(new NullClassProvider()));

        var resolved = registry.resolve(null, "null-class");

        assertSame(IndexedTask.class, resolved.taskClass());
        assertSame(IndexedTask.class, resolved.task().getClass());
    }

    @Test
    void resolvesOriginalMetadataAfterAWrapperCreatesTheTask() {
        var registry = new TaskRegistry(List.of(new WrapperProvider(), new OriginalMetadataProvider()));

        var resolved = registry.resolve(null, "wrapped");

        assertSame(WrapperTask.class, resolved.task().getClass());
        assertSame(OriginalTask.class, resolved.taskClass());
    }

    @Priority(-10)
    private static final class EarlierProvider implements TaskProvider {
        @Override
        public Task createTask(Context context, String key) {
            return Set.of("shared", "dynamic").contains(key) ? new EarlierTask() : null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Set.of("shared", "dynamic").contains(key) ? EarlierTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return Set.of("shared", "dynamic").contains(key);
        }

        @Override
        public Set<String> names() {
            return Set.of();
        }
    }

    @Priority(10)
    private static final class LaterProvider implements TaskProvider {
        @Override
        public Task createTask(Context context, String key) {
            return switch (key) {
                case "shared" -> new LaterTask();
                case "fallback" -> new LaterTask();
                default -> null;
            };
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return Set.of("shared", "fallback").contains(key) ? LaterTask.class : null;
        }

        @Override
        public boolean hasTask(String key) {
            return Set.of("shared", "fallback").contains(key);
        }

        @Override
        public Set<String> names() {
            return Set.of("shared", "fallback");
        }
    }

    @Priority(-10)
    private static final class DuplicateEarlierProvider extends NamedProvider {
        private DuplicateEarlierProvider() {
            super("duplicate");
        }

        @Override
        public Task createTask(Context context, String key) {
            return "duplicate".equals(key) ? new EarlierTask() : null;
        }
    }

    @Priority(10)
    private static final class DuplicateLaterProvider extends NamedProvider {
        private DuplicateLaterProvider() {
            super("duplicate");
        }

        @Override
        public Task createTask(Context context, String key) {
            return "duplicate".equals(key) ? new LaterTask() : null;
        }
    }

    private static final class NullClassProvider extends NamedProvider {
        private NullClassProvider() {
            super("null-class");
        }

        @Override
        public Task createTask(Context context, String key) {
            return "null-class".equals(key) ? new IndexedTask() : null;
        }
    }

    @Priority(-10)
    private static final class WrapperProvider extends NamedProvider {
        private WrapperProvider() {
            super("wrapped");
        }

        @Override
        public Task createTask(Context context, String key) {
            return "wrapped".equals(key) ? new WrapperTask() : null;
        }
    }

    private static final class OriginalMetadataProvider extends NamedProvider {
        private OriginalMetadataProvider() {
            super("wrapped");
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return "wrapped".equals(key) ? OriginalTask.class : null;
        }
    }

    private static final class CountingProvider implements TaskProvider {

        private final String name;
        private final Class<? extends Task> taskClass;
        private int createTaskCalls;
        private int taskClassCalls;
        private int hasTaskCalls;

        private CountingProvider(String name, Class<? extends Task> taskClass) {
            this.name = name;
            this.taskClass = taskClass;
        }

        @Override
        public Task createTask(Context context, String key) {
            createTaskCalls++;
            if (!name.equals(key)) {
                return null;
            }
            return taskClass == IndexedTask.class ? new IndexedTask() : new LaterTask();
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            taskClassCalls++;
            return name.equals(key) ? taskClass : null;
        }

        @Override
        public boolean hasTask(String key) {
            hasTaskCalls++;
            return name.equals(key);
        }

        @Override
        public Set<String> names() {
            return Set.of(name);
        }
    }

    private abstract static class NamedProvider implements TaskProvider {

        private final Set<String> names;

        private NamedProvider(String name) {
            this.names = Set.of(name);
        }

        @Override
        public Task createTask(Context context, String key) {
            return null;
        }

        @Override
        public Class<? extends Task> getTaskClass(Context context, String key) {
            return null;
        }

        @Override
        public boolean hasTask(String key) {
            return names.contains(key);
        }

        @Override
        public Set<String> names() {
            return names;
        }
    }

    private static final class EarlierTask implements Task {
    }

    private static final class LaterTask implements Task {
    }

    private static final class IndexedTask implements Task {
    }

    private static final class WrapperTask implements Task {
    }

    private static final class OriginalTask implements Task {
    }
}
