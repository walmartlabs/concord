package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        String eventName = payload.getHeader(Payload.RESUME_EVENT_NAME);
        if (eventName == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME);

        try {
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
            }

            Path resumeMarker = stateDir.resolve(Constants.Files.RESUME_MARKER_FILE_NAME);
            Files.write(resumeMarker, eventName.getBytes());
        } catch (IOException e) {
            logManager.error(payload.getInstanceId(), "Error while saving resume state", e);
            throw new ProcessException("Error while saving resume state", e);
        }

        return chain.process(payload);
    }
}
