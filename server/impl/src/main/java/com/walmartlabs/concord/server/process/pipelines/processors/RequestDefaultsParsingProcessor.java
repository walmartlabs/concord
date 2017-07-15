package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.common.ConfigurationUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Named
@Singleton
public class RequestDefaultsParsingProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LogManager logManager;

    @Inject
    public RequestDefaultsParsingProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        String instanceId = payload.getInstanceId();

        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        Path p = workspace.resolve(Constants.Files.REQUEST_DEFAULTS_FILE_NAME);
        if (!Files.exists(p)) {
            return chain.process(payload);
        }

        Map<String, Object> a;
        try (InputStream in = Files.newInputStream(p)) {
            a = objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while reading request defaults: " + p, e);
            throw new ProcessException(instanceId, "Error while reading request defaults: " + p, e);
        }

        Map<String, Object> b = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (b == null) {
            b = Collections.emptyMap();
        }

        Map<String, Object> result = ConfigurationUtils.deepMerge(a, b);
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, result);
        return chain.process(payload);
    }
}
