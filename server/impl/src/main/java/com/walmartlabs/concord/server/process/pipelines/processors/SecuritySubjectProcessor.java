package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;

import javax.inject.Inject;

public class SecuritySubjectProcessor implements PayloadProcessor {

    private final ProcessSecurityContext securityContext;

    @Inject
    public SecuritySubjectProcessor(ProcessSecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        securityContext.storeCurrentSubject(payload.getInstanceId());
        return chain.process(payload);
    }
}
