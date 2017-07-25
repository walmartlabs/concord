package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Stores payload's request data as a JSON file.
 */
public class RequestDataStoringProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(RequestDataStoringProcessor.class);

    private final LogManager logManager;

    @Inject
    public RequestDataStoringProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);

        try (OutputStream out = Files.newOutputStream(dst)) {
            ObjectMapper om = new ObjectMapper();
            om.writeValue(out, cfg);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while saving a metadata file: " + dst, e);
            throw new ProcessException(instanceId, "Error while saving a metadata file: " + dst, e);
        }

        log.info("process ['{}'] -> done", instanceId);
        return chain.process(payload);
    }
}
