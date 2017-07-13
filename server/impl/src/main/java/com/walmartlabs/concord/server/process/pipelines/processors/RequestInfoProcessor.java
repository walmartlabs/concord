package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Named;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class RequestInfoProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) req.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            req.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        if (args.containsKey(Constants.Request.REQUEST_INFO_KEY)) {
            return chain.process(payload);
        }

        args.put(Constants.Request.REQUEST_INFO_KEY, new HashMap<>());

        payload = payload.putHeader(Payload.REQUEST_DATA_MAP, req);
        return chain.process(payload);
    }

    public static Map<String, Object> createRequestInfo(UriInfo i) {
        Map<String, Object> queryParams = new HashMap<>();
        for (Map.Entry<String, List<String>> e : i.getQueryParameters(true).entrySet()) {
            String k = e.getKey();
            List<String> v = e.getValue();

            if (v.size() == 1) {
                queryParams.put(k, v.get(0));
            } else {
                queryParams.put(k, v);
            }
        }

        Map<String, Object> m = new HashMap<>();
        m.put("uri", i.getRequestUri());
        m.put("query", queryParams);
        return m;
    }
}
