package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Named
public class OutVariablesSettingProcessor implements PayloadProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Set<String> outExpr = payload.getHeader(Payload.OUT_EXPRESSIONS);
        if (outExpr == null) {
            return chain.process(payload);
        }

        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        req.put(InternalConstants.Request.OUT_EXPRESSIONS_KEY, outExpr);
        payload = payload.mergeValues(Payload.REQUEST_DATA_MAP,
                Collections.singletonMap(InternalConstants.Request.OUT_EXPRESSIONS_KEY, outExpr));

        return chain.process(payload);
    }
}
