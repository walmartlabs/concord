package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.util.*;

@Named
public class TagsExtractingProcessor implements PayloadProcessor {

    @Override
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        Object v = req.get(InternalConstants.Request.TAGS_KEY);
        if (v == null) {
            return chain.process(payload);
        }

        Set<String> tags;

        if (v instanceof String[]) {
            String[] as = (String[]) v;
            tags = new HashSet<>(as.length);
            Collections.addAll(tags, as);
        } else if (v instanceof Collection) {
            Collection c = (Collection) v;
            tags = new HashSet<>(c.size());

            for (Object o : c) {
                if (o instanceof String) {
                    tags.add((String) o);
                } else {
                    throw new ProcessException(payload.getInstanceId(), "Process tag must be a string value: " + o);
                }
            }
        } else {
            throw new ProcessException(payload.getInstanceId(), "Process tags must be an array of string values: " + v);
        }

        payload = payload.putHeader(Payload.PROCESS_TAGS, tags);

        return chain.process(payload);
    }
}
