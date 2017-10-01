package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Named
public class ForkCleanupProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path markerDir = workspace.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .resolve(Constants.Files.JOB_STATE_DIR_NAME);

            String[] markers = {Constants.Files.SUSPEND_MARKER_FILE_NAME, Constants.Files.RESUME_MARKER_FILE_NAME};
            for (String m : markers) {
                Path suspendMarker = markerDir.resolve(m);
                Files.deleteIfExists(suspendMarker);
            }
        } catch (IOException e) {
            throw new ProcessException(payload.getInstanceId(), "Error while preparing a fork's data", e);
        }

        return chain.process(payload);
    }
}
