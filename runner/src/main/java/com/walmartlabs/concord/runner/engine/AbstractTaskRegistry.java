package com.walmartlabs.concord.runner.engine;

import com.google.inject.Injector;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(AbstractTaskRegistry.class);

    private final Map<String, Task> singletonTasks = new ConcurrentHashMap<>();

    private final Map<String, Class<? extends Task>> tasks = new ConcurrentHashMap<>();

    protected Task get(String name, Injector injector) {
        Class<? extends Task> taskClass = tasks.get(name);
        if(taskClass == null) {
            return null;
        }

        if(taskClass.isAnnotationPresent(Singleton.class)) {
            return singletonTasks.computeIfAbsent(name, k -> injector.getInstance(taskClass));
        }

        return injector.getInstance(taskClass);
    }

    protected void register(String name, Class<? extends Task> taskClass) {
        if (tasks.putIfAbsent(name, taskClass) != null) {
            throw new IllegalArgumentException("Non-unique task name: " + name);
        }
        log.debug("register ['{}'] -> done", name);
    }
}
