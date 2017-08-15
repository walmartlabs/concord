package com.walmartlabs.concord.runner.engine;

import com.google.inject.Injector;
import com.walmartlabs.concord.common.DynamicTaskRegistry;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.eclipse.sisu.EagerSingleton;

import javax.inject.Inject;
import javax.inject.Named;

@Named
@EagerSingleton
public class NamedTaskRegistry extends AbstractTaskRegistry implements ServiceTaskRegistry {

    private final DynamicTaskRegistry dynamicTasks;
    private final Injector injector;

    @Inject
    public NamedTaskRegistry(Injector injector,
                             DynamicTaskRegistry dynamicTasks) {

        this.injector = injector;
        this.dynamicTasks = dynamicTasks;

        // TODO: find a way to inject task classes directly
        TaskClassHolder h = TaskClassHolder.getInstance();
        h.getTasks().forEach(this::register);
    }

    @Override
    public Object getByKey(String key) {
        Object o = null;
        if (dynamicTasks != null) {
            o = dynamicTasks.getByKey(key);
        }

        return o != null ? o : get(key, injector);
    }
}
