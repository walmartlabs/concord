package com.walmartlabs.concord.server.process.pipelines;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.pipelines.processors.*;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ForkPipeline extends Pipeline {

    private final ExceptionProcessor exceptionProcessor;

    @Inject
    public ForkPipeline(Injector injector) {
        super(injector,
                InitialQueueEntryProcessor.class,
                ForkCleanupProcessor.class,
                ForkDataMergingProcessor.class,
                TagsExtractingProcessor.class,
                OutVariablesSettingProcessor.class,
                RequestDataStoringProcessor.class,
                StateImportingProcessor.class,
                FlowMetadataProcessor.class,
                EnqueueingProcessor.class);

        this.exceptionProcessor = injector.getInstance(FailProcessor.class);
    }

    @Override
    protected ExceptionProcessor getExceptionProcessor() {
        return exceptionProcessor;
    }
}
