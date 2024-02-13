package com.walmartlabs.concord.server.process.pipelines;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.google.inject.Injector;
import com.walmartlabs.concord.server.ansible.InventoryProcessor;
import com.walmartlabs.concord.server.ansible.PrivateKeyProcessor;
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Handles NEW "regular" processes. Puts the processes into the ENQUEUED status.
 * Forks are processed by {@link ForkPipeline}.
 */
@Named
public class EnqueueProcessPipeline extends Pipeline {

    private final ExceptionProcessor exceptionProcessor;
    private final FinalizerProcessor finalizerProcessor;

    @Inject
    public EnqueueProcessPipeline(Injector injector) {
        super(injector,
                LoggingMDCProcessor.class,
                PayloadRestoreProcessor.class,
                RunAsCurrentProcessUserProcessor.class,
                PolicyExportProcessor.class,
                WorkspaceArchiveProcessor.class,
                RepositoryProcessor.class,
                RepositoryInfoUpdateProcessor.class,
                InventoryProcessor.class,
                AttachmentStoringProcessor.class,
                ProcessDefinitionProcessor.class,
                SessionTokenProcessor.class,
                ConfigurationProcessor.class,
                AssertOutVariablesProcessor.class,
                ExclusiveGroupProcessor.class,
                EntryPointProcessor.class,
                TagsExtractingProcessor.class,
                PrivateKeyProcessor.class,
                TemplateFilesProcessor.class,
                TemplateScriptProcessor.class,
                DependenciesProcessor.class,
                InitiatorUserInfoProcessor.class,
                OutVariablesSettingProcessor.class,
                ResumeEventsProcessor.class,
                ConfigurationStoringProcessor.class,
                PolicyProcessor.class,
                DependencyVersionsExportProcessor.class,
                StateImportingProcessor.class,
                ProcessHandlersProcessor.class,
                EffectiveProcessDefinitionProcessor.class,
                SecuritySubjectProcessor.class,
                EnqueueingProcessor.class);

        this.exceptionProcessor = injector.getInstance(FailProcessor.class);
        this.finalizerProcessor = injector.getInstance(CleanupProcessor.class);
    }

    @Override
    protected ExceptionProcessor getExceptionProcessor() {
        return exceptionProcessor;
    }

    @Override
    protected FinalizerProcessor getFinalizerProcessor() {
        return finalizerProcessor;
    }
}
