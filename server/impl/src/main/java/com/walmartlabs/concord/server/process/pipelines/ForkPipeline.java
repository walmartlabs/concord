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
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import java.util.List;

/**
 * Handles processes forked from a parent (including "handler"
 * processes such as "onCancel", "onFailure", etc).
 */
public class ForkPipeline extends Pipeline {

    private final ExceptionProcessor exceptionProcessor;
    private final FinalizerProcessor finalizerProcessor;

    @Inject
    public ForkPipeline(Injector injector) {
        super(List.of(
                injector.getInstance(LoggingMDCProcessor.class),
                injector.getInstance(PolicyExportProcessor.class),
                injector.getInstance(InitialQueueEntryProcessor.class),
                injector.getInstance(ForkPolicyProcessor.class),
                injector.getInstance(ForkCleanupProcessor.class),
                injector.getInstance(SessionTokenProcessor.class),
                injector.getInstance(ConfigurationProcessor.class),
                injector.getInstance(TagsExtractingProcessor.class),
                injector.getInstance(InitiatorUserInfoProcessor.class),
                injector.getInstance(OutVariablesSettingProcessor.class),
                injector.getInstance(ConfigurationStoringProcessor.class),
                injector.getInstance(StateImportingProcessor.class),
                injector.getInstance(ForkHandlersProcessor.class),
                injector.getInstance(ForkRepositoryInfoProcessor.class),
                injector.getInstance(RepositoryInfoUpdateProcessor.class),
                injector.getInstance(ForkRuntimeProcessor.class),
                injector.getInstance(EnqueueingProcessor.class)
        ));

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
