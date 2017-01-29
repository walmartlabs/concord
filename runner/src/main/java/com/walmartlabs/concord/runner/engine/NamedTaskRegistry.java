package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Named
public class NamedTaskRegistry implements ServiceTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(NamedTaskRegistry.class);

    private final Map<String, Task> tasks;

    @Inject
    public NamedTaskRegistry(Collection<Task> tasks) {
        Map<String, Task> m = new HashMap<>();
        for (Task t : tasks) {
            String k = t.getKey();
            m.put(k, t);
            log.debug("init -> got '{}' - {}", k, t.getClass());
        }

        this.tasks = m;
    }

    @Override
    public Object getByKey(String key) {
        return tasks.get(key);
    }
}
