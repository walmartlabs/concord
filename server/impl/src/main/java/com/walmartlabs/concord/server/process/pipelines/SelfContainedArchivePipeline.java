package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.template.TemplateProcessor;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class SelfContainedArchivePipeline extends Chain {

    @Inject
    public SelfContainedArchivePipeline(Injector injector) {
        super(injector.getInstance(WorkspaceArchiveProcessor.class),
                injector.getInstance(RequestDefaultsParsingProcessor.class),
                injector.getInstance(TemplateProcessor.class),
                injector.getInstance(DependenciesProcessor.class),
                injector.getInstance(RequestDataStoringProcessor.class),
                injector.getInstance(RunnerProcessor.class),
                injector.getInstance(LogFileProcessor.class),
                injector.getInstance(ValidatingProcessor.class),
                injector.getInstance(ArchivingProcessor.class),
                injector.getInstance(AgentProcessor.class),
                injector.getInstance(AttachmentsSavingProcessor.class),
                injector.getInstance(StatusFinalizingProcessor.class));
    }
}
