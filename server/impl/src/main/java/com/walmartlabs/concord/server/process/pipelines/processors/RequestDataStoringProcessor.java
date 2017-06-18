package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        Map<String, Object> meta = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (meta == null) {
            return chain.process(payload);
        }

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        Path dst = workspace.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);

        ObjectMapper om = new ObjectMapper();
        try {
            Map<String, Object> prev = Collections.emptyMap();
            if (Files.exists(dst)) {
                try (InputStream in = Files.newInputStream(dst)) {
                    prev = om.readValue(in, Map.class);
                }
            }

            // merge everything except "arguments"
            Map<String, Object> data = new HashMap<>(prev);
            data.remove(Constants.Request.ARGUMENTS_KEY);
            ConfigurationUtils.deepMerge(data, meta);

            try (Writer writer = Files.newBufferedWriter(dst)) {
                om.writeValue(writer, data);
            }
        } catch (IOException e) {
            logManager.error(payload.getInstanceId(), "Error while saving a metadata file: " + dst, e);
            throw new ProcessException("Error while saving a metadata file: " + dst, e);
        }

        log.info("process ['{}'] -> done", payload.getInstanceId());
        return chain.process(payload);
    }
}
