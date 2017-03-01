package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.template.TemplateProcessor;

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
                injector.getInstance(TemplateProcessor.class),
                injector.getInstance(DependenciesProcessor.class),
                injector.getInstance(RequestDataStoringProcessor.class),
                injector.getInstance(RunnerProcessor.class),
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
