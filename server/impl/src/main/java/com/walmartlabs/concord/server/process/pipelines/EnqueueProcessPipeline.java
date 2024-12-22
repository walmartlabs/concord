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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.*;
import com.walmartlabs.concord.server.sdk.events.PipelineEvent;
import com.walmartlabs.concord.server.sdk.events.PipelineEventListener;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Set;

import static com.walmartlabs.concord.server.sdk.events.PipelineEvent.EventType.ENQUEUE_PROCESS_PIPELINE_END;
import static com.walmartlabs.concord.server.sdk.events.PipelineEvent.EventType.ENQUEUE_PROCESS_PIPELINE_START;
import static java.util.Objects.requireNonNull;

/**
 * Handles NEW "regular" processes. Puts the processes into the ENQUEUED status.
 * Forks are processed by {@link ForkPipeline}.
 */
@Named
public class EnqueueProcessPipeline extends Pipeline {

    private final ExceptionProcessor exceptionProcessor;
    private final FinalizerProcessor finalizerProcessor;
    private final Set<PipelineEventListener> eventListeners;

    @Inject
    public EnqueueProcessPipeline(Injector injector,
                                  CustomEnqueueProcessors customProcessors,
                                  Set<PipelineEventListener> eventListeners) {
        super(List.of(
                injector.getInstance(LoggingMDCProcessor.class),
                injector.getInstance(PayloadRestoreProcessor.class),
                injector.getInstance(RunAsCurrentProcessUserProcessor.class),
                injector.getInstance(PolicyExportProcessor.class),
                injector.getInstance(WorkspaceArchiveProcessor.class),
                injector.getInstance(RepositoryProcessor.class),
                injector.getInstance(RepositoryInfoUpdateProcessor.class),
                customProcessors.handleAttachments(),
                injector.getInstance(AttachmentStoringProcessor.class),
                injector.getInstance(ProcessDefinitionProcessor.class),
                injector.getInstance(SessionTokenProcessor.class),
                injector.getInstance(ConfigurationProcessor.class),
                injector.getInstance(AssertOutVariablesProcessor.class),
                injector.getInstance(ExclusiveGroupProcessor.class),
                injector.getInstance(EntryPointProcessor.class),
                injector.getInstance(TagsExtractingProcessor.class),
                injector.getInstance(TemplateFilesProcessor.class),
                injector.getInstance(TemplateScriptProcessor.class),
                injector.getInstance(DependenciesProcessor.class),
                injector.getInstance(InitiatorUserInfoProcessor.class),
                injector.getInstance(OutVariablesSettingProcessor.class),
                injector.getInstance(ResumeEventsProcessor.class),
                injector.getInstance(ConfigurationStoringProcessor.class),
                injector.getInstance(PolicyProcessor.class),
                injector.getInstance(DependencyVersionsExportProcessor.class),
                customProcessors.handleState(),
                injector.getInstance(StateImportingProcessor.class),
                injector.getInstance(ProcessHandlersProcessor.class),
                injector.getInstance(EffectiveProcessDefinitionProcessor.class),
                injector.getInstance(SecuritySubjectProcessor.class),
                injector.getInstance(EnqueueingProcessor.class)
        ));

        this.exceptionProcessor = injector.getInstance(FailProcessor.class);
        this.finalizerProcessor = injector.getInstance(CleanupProcessor.class);

        this.eventListeners = requireNonNull(eventListeners);
    }

    @Override
    public Payload process(Payload payload) {
        var startEvent = PipelineEvent.builder()
                .eventType(ENQUEUE_PROCESS_PIPELINE_START)
                .processKey(payload.getProcessKey())
                .build();
        eventListeners.forEach(l -> l.onPipelineEvent(startEvent));

        try {
            var result = super.process(payload);

            var endEvent = PipelineEvent.builder()
                    .eventType(ENQUEUE_PROCESS_PIPELINE_END)
                    .processKey(payload.getProcessKey())
                    .build();
            eventListeners.forEach(l -> l.onPipelineEvent(endEvent));

            return result;
        } catch (Throwable error) {
            var errorEvent = PipelineEvent.builder()
                    .eventType(ENQUEUE_PROCESS_PIPELINE_END)
                    .processKey(payload.getProcessKey())
                    .build();
            eventListeners.forEach(l -> l.onPipelineError(errorEvent, error));

            throw error;
        }
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
