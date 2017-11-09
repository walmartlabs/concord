package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.ansible.InventoryProcessor;
import com.walmartlabs.concord.server.ansible.PrivateKeyProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Processing project requests.
 * <p>
 * Runs a process by pulling a project's repository and applying
 * overrides from a request JSON.
 */
@Named
public class ProjectPipeline extends Pipeline {

    private final ExceptionProcessor exceptionProcessor;

    @Inject
    public ProjectPipeline(Injector injector) {
        super(injector,
                InitialQueueEntryProcessor.class,
                WorkspaceArchiveProcessor.class,
                RepositoryProcessor.class,
                ProjectDefinitionProcessor.class,
                InventoryProcessor.class,
                AttachmentStoringProcessor.class,
                RequestDataMergingProcessor.class,
                TagsExtractingProcessor.class,
                PrivateKeyProcessor.class,
                TemplateFilesProcessor.class,
                TemplateScriptProcessor.class,
                RequestInfoProcessor.class,
                ProjectInfoProcessor.class,
                DependenciesProcessor.class,
                UserInfoProcessor.class,
                OutVariablesSettingProcessor.class,
                RequestDataStoringProcessor.class,
                StateImportingProcessor.class,
                FlowMetadataProcessor.class,
                SecuritySubjectProcessor.class,
                EnqueueingProcessor.class);

        this.exceptionProcessor = injector.getInstance(FailProcessor.class);
    }

    @Override
    protected ExceptionProcessor getExceptionProcessor() {
        return exceptionProcessor;
    }
}
