package com.walmartlabs.concord.server.process.pipelines.processors;

import com.google.inject.Injector;
import com.walmartlabs.concord.server.process.Payload;

import java.util.stream.Stream;

public abstract class Pipeline extends Chain {

    @SafeVarargs
    public Pipeline(
            Injector injector,
            Class<? extends PayloadProcessor> ... processors) {
        super(Stream.of(processors)
                .map(injector::getInstance)
                .toArray(PayloadProcessor[]::new));
    }

    @Override
    public Payload process(Payload payload) {
        try {
            return super.process(payload);
        } catch (Exception e) {
            ExceptionProcessor p = getExceptionProcessor();
            if(p != null) {
                p.process(payload, e);
            }
            throw e;
        }
    }

    protected ExceptionProcessor getExceptionProcessor() {
        return null;
    }
}
