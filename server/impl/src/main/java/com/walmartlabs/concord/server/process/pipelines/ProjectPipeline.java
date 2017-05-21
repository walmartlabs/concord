package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.ansible.InventoryProcessor;
import com.walmartlabs.concord.server.ansible.PrivateKeyProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.project.RepositoryProcessor;
import com.walmartlabs.concord.server.template.TemplateProcessor;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Processing project requests.
 * <p>
 * Runs a process by pulling a project's repository and applying
 * overrides from a request JSON.
 */
@Named
public class ProjectPipeline extends Chain {

    @Inject
    public ProjectPipeline(Injector injector) {
        super(injector.getInstance(RequestDataParsingProcessor.class),
                injector.getInstance(RepositoryProcessor.class),
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
                injector.getInstance(LogFileProcessor.class),
                injector.getInstance(ValidatingProcessor.class),
                injector.getInstance(EnqueueingProcessor.class)
        );
    }
}
