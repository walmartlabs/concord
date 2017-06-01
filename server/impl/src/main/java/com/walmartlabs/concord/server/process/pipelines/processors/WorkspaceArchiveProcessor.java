package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

/**
 * Unpacks payload's workspace file, parses request data.
 */
@Named
public class WorkspaceArchiveProcessor implements PayloadProcessor {

    private final LogManager logManager;

    @Inject
    public WorkspaceArchiveProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        Path archive = payload.getAttachment(Payload.WORKSPACE_ARCHIVE);
        if (archive == null) {
            return chain.process(payload);
        }

        if (!Files.exists(archive)) {
            logManager.error(payload.getInstanceId(), "No input archive found: " + archive);
            throw new ProcessException("No input archive found: " + archive, Status.BAD_REQUEST);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            IOUtils.unzip(zip, workspace);
        } catch (IOException e) {
            logManager.error(payload.getInstanceId(), "Error while unpacking an archive: " + archive, e);
            throw new ProcessException("Error while unpacking an archive: " + archive, e);
        }

        payload = payload.removeAttachment(Payload.WORKSPACE_ARCHIVE);
        return chain.process(payload);
    }
}
