package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.common.Task;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Named
@Singleton
public class DynamicTaskRegistryImpl implements DynamicTaskRegistry {

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public Task getByKey(String key) {
        return tasks.get(key);
    }

    @Override
    public void register(Task task) {
        Named n = task.getClass().getAnnotation(Named.class);
        if (n == null) {
            throw new IllegalArgumentException("Tasks must be annotated with @Named");
        }

        if (tasks.putIfAbsent(n.value(), task) != null) {
            throw new IllegalArgumentException("Non-unique task name: " + n.value());
        }
    }
}
