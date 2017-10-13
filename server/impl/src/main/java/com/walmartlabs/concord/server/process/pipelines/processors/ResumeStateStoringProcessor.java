package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Named
public class ResumeStateStoringProcessor implements PayloadProcessor {


    private final LogManager logManager;

    @Inject
    public ResumeStateStoringProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        String eventName = payload.getHeader(Payload.RESUME_EVENT_NAME);
        if (eventName == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME);

        try {
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
            }

            Path resumeMarker = stateDir.resolve(InternalConstants.Files.RESUME_MARKER_FILE_NAME);
            Files.write(resumeMarker, eventName.getBytes());
        } catch (IOException e) {
            logManager.error(instanceId, "Error while saving resume state", e);
            throw new ProcessException(instanceId, "Error while saving resume state", e);
        }

        return chain.process(payload);
    }
}
