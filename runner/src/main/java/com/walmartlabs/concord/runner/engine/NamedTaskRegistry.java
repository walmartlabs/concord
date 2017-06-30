package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

@Named
public class NamedTaskRegistry implements ServiceTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(NamedTaskRegistry.class);

    private final Map<String, Task> tasks;
    private final DynamicTaskRegistry dynamicTasks;

    @Inject
    public NamedTaskRegistry(Map<String, Task> tasks, DynamicTaskRegistry dynamicTasks) {
        this.tasks = tasks;
        this.dynamicTasks = dynamicTasks;
    }

    public NamedTaskRegistry(Task... tasks) {
        this(toMap(tasks), null);
    }

    @Override
    public Object getByKey(String key) {
        Object o = null;
        if (dynamicTasks != null) {
            o = dynamicTasks.getByKey(key);
        }

        return o != null ? o : tasks.get(key);
    }

    private static Map<String, Task> toMap(Task[] tasks) {
        Map<String, Task> m = new HashMap<>();
        for (Task t : tasks) {
            Named n = t.getClass().getAnnotation(Named.class);
            if (n == null || n.value() == null) {
                log.warn("init -> skipping {}, invalid @Named annotation", t);
                continue;
            }

            m.put(n.value(), t);
        }
        return m;
    }
}
