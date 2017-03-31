package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.ansible.InlineInventoryProcessor;
import com.walmartlabs.concord.server.ansible.InventoryProcessor;
import com.walmartlabs.concord.server.ansible.PrivateKeyProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.project.RepositoryProcessor;
import com.walmartlabs.concord.server.template.TemplateProcessor;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ProjectPipeline extends Chain {

    @Inject
    public ProjectPipeline(Injector injector) {
        super(injector.getInstance(RequestDataParsingProcessor.class),
                injector.getInstance(ProjectConfigurationProcessor.class),
                injector.getInstance(RepositoryProcessor.class),
                injector.getInstance(RequestDefaultsParsingProcessor.class),
                injector.getInstance(InventoryProcessor.class),
                injector.getInstance(InlineInventoryProcessor.class),
                injector.getInstance(PrivateKeyProcessor.class),
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
