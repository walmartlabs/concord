package com.walmartlabs.concord.server.process.pipelines.processors;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Set;

@Named
public class CustomEnqueueProcessors {

    private final Collection<CustomEnqueueProcessor> processors;

    @Inject
    public CustomEnqueueProcessors(Set<CustomEnqueueProcessor> processors) {
        this.processors = processors;
    }

    public Collection<CustomEnqueueProcessor> get() {
        return processors;
    }
}
