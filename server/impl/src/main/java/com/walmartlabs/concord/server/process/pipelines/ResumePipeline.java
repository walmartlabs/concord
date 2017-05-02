package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ResumePipeline extends Chain {

    @Inject
    public ResumePipeline(Injector injector) {
        super(injector.getInstance(ResumeStateStoringProcessor.class),
                injector.getInstance(RequestDataStoringProcessor.class),
                injector.getInstance(LogFileProcessor.class),
                injector.getInstance(ArchivingProcessor.class),
                injector.getInstance(AgentProcessor.class),
                injector.getInstance(StateTransferringProcessor.class),
                injector.getInstance(StatusFinalizingProcessor.class));
    }
}
