package com.walmartlabs.concord.server.process.pipelines.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.keys.AttachmentKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parses request's data and stores it as header values.
 */
@Named
public class RequestDataParsingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(RequestDataParsingProcessor.class);

    public static final AttachmentKey REQUEST_ATTACHMENT_KEY = AttachmentKey.register("request");

    @SuppressWarnings("unchecked")
    @Override
    public Payload process(Chain chain, Payload payload) {
        Path p = payload.getAttachment(REQUEST_ATTACHMENT_KEY);
        if (p == null) {
            return chain.process(payload);
        }

        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            data = om.readValue(in, Map.class);
        } catch (IOException e) {
            log.error("process ['{}'] -> error while parsing a request data attachment", payload);
            throw new ProcessException("Invalid request data format", e, Status.BAD_REQUEST);
        }

        payload = payload.removeAttachment(REQUEST_ATTACHMENT_KEY)
                .putHeader(Payload.REQUEST_DATA_MAP, data);

        return chain.process(payload);
    }
}
