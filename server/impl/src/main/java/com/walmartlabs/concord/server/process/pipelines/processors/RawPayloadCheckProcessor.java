package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class RawPayloadCheckProcessor implements PayloadProcessor {

    private final PayloadManager payloadManager;

    @Inject
    public RawPayloadCheckProcessor(PayloadManager payloadManager) {
        this.payloadManager = payloadManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        payloadManager.assertAcceptsRawPayload(payload);
        return chain.process(payload);
    }
}
