package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
public class ResumeStateStoringProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        String eventName = payload.getHeader(Payload.RESUME_EVENT_NAME);
        if (eventName == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path stateDir = workspace.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME);

        try {
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
            }

            Path resumeMarker = stateDir.resolve(Constants.RESUME_MARKER_FILE_NAME);
            Files.write(resumeMarker, eventName.getBytes());
        } catch (IOException e) {
            throw new ProcessException("Error while saving resume state", e);
        }

        return chain.process(payload);
    }
}
