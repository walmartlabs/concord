package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ResumePipeline extends Pipeline {

    @Inject
    public ResumePipeline(Injector injector) {
        super(injector,
                ResumeStateStoringProcessor.class,
                RequestDataStoringProcessor.class,
                StateImportingProcessor.class,
                EnqueueingResumeProcessor.class);
    }
}
