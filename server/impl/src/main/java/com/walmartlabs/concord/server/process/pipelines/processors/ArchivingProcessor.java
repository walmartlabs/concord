package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

/**
 * Creates a payload archive.
 */
@Named
public class ArchivingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ArchivingProcessor.class);
    private static final String[] IGNORED_FILES = {"\\.git"};

    public static final HeaderKey<Path> ARCHIVE_FILE = HeaderKey.register("_archive", Path.class);

    @Override
    public Payload process(Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path dst = Files.createTempFile("payload", ".zip");

            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(dst)))) {
                IOUtils.zip(zip, workspace, IGNORED_FILES);
                log.info("process ['{}'] -> archive: {}", payload.getInstanceId(), dst);
            }

            return payload.removeHeader(Payload.WORKSPACE_DIR)
                    .putHeader(ARCHIVE_FILE, dst);
        } catch (IOException e) {
            throw new WebApplicationException("Error while creating a payload archive", e);
        }
    }
}
