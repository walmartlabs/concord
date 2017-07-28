package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import java.util.HashMap;
import java.util.Map;

public class TaskClassHolder {

    private static final TaskClassHolder INSTANCE = new TaskClassHolder();

    public static TaskClassHolder getInstance() {
        return INSTANCE;
    }

    private final Map<String, Class<? extends Task>> tasks = new HashMap<>();

    public void register(String name, Class<? extends Task> task) {
        tasks.putIfAbsent(name, task);
    }

    public Map<String, Class<? extends Task>> getTasks() {
        return tasks;
    }

    private TaskClassHolder() {
    }
}
