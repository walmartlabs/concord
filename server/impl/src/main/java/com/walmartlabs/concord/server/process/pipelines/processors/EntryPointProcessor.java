package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

@Named
public class EntryPointProcessor implements PayloadProcessor {

    private final LogManager logManager;

    @Inject
    public EntryPointProcessor(LogManager logManager) {
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String s = payload.getHeader(Payload.ENTRY_POINT);

        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        if (s == null) {
            s = (String) cfg.get(Constants.Request.ENTRY_POINT_KEY);
        }

        if (s == null) {
            s = Constants.Request.DEFAULT_ENTRY_POINT_NAME;
        }

        cfg.put(Constants.Request.ENTRY_POINT_KEY, s);
        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, cfg);

        logManager.info(payload.getInstanceId(), "Using entry point: {}", s);

        return chain.process(payload);
    }
}
