package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

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

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path archive = payload.getAttachment(Payload.WORKSPACE_ARCHIVE);
        if (archive == null) {
            return chain.process(payload);
        }

        if (!Files.exists(archive)) {
            throw new ProcessException("No input archive found: " + archive, Status.BAD_REQUEST);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            IOUtils.unzip(zip, workspace);
        } catch (IOException e) {
            throw new ProcessException("Error while unpacking an archive: " + archive, e);
        }

        payload = payload.removeAttachment(Payload.WORKSPACE_ARCHIVE);
        return chain.process(payload);
    }
}
