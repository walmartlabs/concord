package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.project.ConfigurationUtils;

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
public class RequestDefaultsParstingProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Payload process(Chain chain, Payload payload) {
        Path workspace = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workspace == null) {
            return chain.process(payload);
        }

        Path p = workspace.resolve(Constants.REQUEST_DEFAULTS_FILE_NAME);
        if (!Files.exists(p)) {
            return chain.process(payload);
        }

        Map<String, Object> a;
        try (InputStream in = Files.newInputStream(p)) {
            a = objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ProcessException("Error while reading request defaults: " + p, e);
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
