package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * Unpacks payload's workspace file, parses request data.
 */
@Named
public class WorkspaceArchiveProcessor implements PayloadProcessor {

    @Override
    public Payload process(Payload payload) {
        Path archive = payload.getAttachment(Payload.WORKSPACE_ARCHIVE);
        if (archive == null || !Files.exists(archive)) {
            throw new WebApplicationException("No input archive found: " + archive, Status.BAD_REQUEST);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            IOUtils.unzip(zip, workspace);
        } catch (IOException e) {
            throw new WebApplicationException("Error while unpacking an archive: " + archive, e);
        }

        Path requestFile = workspace.resolve(Constants.REQUEST_DATA_FILE_NAME);
        if (Files.exists(requestFile)) {
            ObjectMapper om = new ObjectMapper();
            try (InputStream in = new BufferedInputStream(Files.newInputStream(requestFile))) {
                Map m = om.readValue(in, Map.class);
                payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, m);
            } catch (IOException e) {
                throw new WebApplicationException("Error while reading a request JSON: " + requestFile, e);
            }
        }

        return payload.removeAttachment(Payload.WORKSPACE_ARCHIVE);
    }
}
