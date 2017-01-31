package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;

public interface PayloadProcessor {

    Payload process(Payload payload);
}
