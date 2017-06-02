package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;

public interface ExceptionProcessor {

    void process(Payload payload, Exception e);
}
