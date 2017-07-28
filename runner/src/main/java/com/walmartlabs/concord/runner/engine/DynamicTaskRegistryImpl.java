package com.walmartlabs.concord.runner.engine;

import com.google.inject.Injector;
import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.common.Task;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class DynamicTaskRegistryImpl extends AbstractTaskRegistry implements DynamicTaskRegistry {

    private final Injector injector;

    @Inject
    public DynamicTaskRegistryImpl(Injector injector) {
        this.injector = injector;
    }

    @Override
    public Task getByKey(String key) {
        return get(key, injector);
    }

    public void register(Class<? extends Task> taskClass) {
        Named n = taskClass.getAnnotation(Named.class);
        if (n == null) {
            throw new IllegalArgumentException("Tasks must be annotated with @Named");
        }

        register(n.value(), taskClass);
    }
}
