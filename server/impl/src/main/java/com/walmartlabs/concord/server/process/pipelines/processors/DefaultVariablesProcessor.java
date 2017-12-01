package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.DefaultVariablesConfiguration;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;

@Named
public class DefaultVariablesProcessor implements PayloadProcessor {

    private final DefaultVariablesConfiguration cfg;

    @Inject
    public DefaultVariablesProcessor(DefaultVariablesConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        req = new HashMap<>(req);

        Map<String, Object> args = (Map<String, Object>) req.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            req.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        args.putAll(cfg.getVars());

        return chain.process(payload.putHeader(Payload.REQUEST_DATA_MAP, req));
    }
}
