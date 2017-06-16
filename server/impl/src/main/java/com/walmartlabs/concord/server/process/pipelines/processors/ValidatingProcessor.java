package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Map;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Validates that no unprocessed attachments left in a payload.
 */
@Named
public class ValidatingProcessor implements PayloadProcessor {

    private final LogManager logManager;

    @Inject
    public ValidatingProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        Map<String, Path> attachments = payload.getAttachments();
        if (!attachments.isEmpty()) {
            String msg = "Validation error, unprocessed payload attachments: " + String.join(", ", attachments.keySet());
            logManager.error(payload.getInstanceId(), msg);
            throw new ProcessException(msg, BAD_REQUEST);
        }

        return chain.process(payload);
    }
}
