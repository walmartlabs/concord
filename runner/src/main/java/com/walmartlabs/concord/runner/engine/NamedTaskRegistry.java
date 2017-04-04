package com.walmartlabs.concord.runner.engine;

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

    @Inject
    public NamedTaskRegistry(Map<String, Task> tasks) {
        this.tasks = tasks;
    }

    public NamedTaskRegistry(Task... tasks) {
        this(toMap(tasks));
    }

    @Override
    public Object getByKey(String key) {
        return tasks.get(key);
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
