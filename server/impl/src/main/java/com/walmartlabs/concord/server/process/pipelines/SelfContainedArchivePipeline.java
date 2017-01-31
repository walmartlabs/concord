package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.inventory.InventoryProcessor;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.repository.RepositoryProcessor;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class SelfContainedArchivePipeline {

    private final PayloadProcessor[] processors;

    @Inject
    public SelfContainedArchivePipeline(Injector injector) {
        // TODO move to a cfg file
        this.processors = new PayloadProcessor[]{
                injector.getInstance(WorkspaceArchiveProcessor.class),
                injector.getInstance(RepositoryProcessor.class),
                injector.getInstance(InventoryProcessor.class),
                injector.getInstance(RequestDataStoringProcessor.class),
                injector.getInstance(DependenciesProcessor.class),
                injector.getInstance(LogFileProcessor.class),
                injector.getInstance(ValidatingProcessor.class),
                injector.getInstance(ArchivingProcessor.class),
                injector.getInstance(AgentProcessor.class)
        };
    }

    public void process(Payload payload) {
        for (PayloadProcessor p : processors) {
            payload = p.process(payload);
        }
    }
}
