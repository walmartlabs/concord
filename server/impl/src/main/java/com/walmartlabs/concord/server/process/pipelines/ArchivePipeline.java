package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.ansible.InventoryProcessor;
import com.walmartlabs.concord.server.ansible.PrivateKeyProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.template.TemplateProcessor;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Processing self-contained archives.
 * <p>
 * Runs a process using an archive, provided by an user.
 */
@Named
public class ArchivePipeline extends Chain {

    @Inject
    public ArchivePipeline(Injector injector) {
        super(injector.getInstance(LogFileProcessor.class),
                injector.getInstance(WorkspaceArchiveProcessor.class),
                injector.getInstance(WorkspaceRequestDataParsingProcessor.class),
                injector.getInstance(RequestDataParsingProcessor.class),
                injector.getInstance(ActiveProfilesProcessor.class),
                injector.getInstance(ProjectDefinitionProcessor.class),
                injector.getInstance(ProjectConfigurationProcessor.class),
                injector.getInstance(RequestDefaultsParsingProcessor.class),
                injector.getInstance(InventoryProcessor.class),
                injector.getInstance(PrivateKeyProcessor.class),
                injector.getInstance(TemplateProcessor.class),
                injector.getInstance(DependenciesProcessor.class),
                injector.getInstance(UserInfoProcessor.class),
                injector.getInstance(RequestDataStoringProcessor.class),
                injector.getInstance(ValidatingProcessor.class),
                injector.getInstance(EnqueueingProcessor.class)
        );
    }
}
