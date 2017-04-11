package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;

import javax.inject.Named;
import java.util.Collection;
import java.util.Map;

@Named
public class ActiveProfilesProcessor implements PayloadProcessor {

    public static final String[] DEFAULT_PROFILES = {"default"};

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg == null) {
            return chain.process(useDefaultProfiles(payload));
        }

        Object v = cfg.get(Constants.Request.ACTIVE_PROFILES_KEY);
        if (v == null) {
            return chain.process(useDefaultProfiles(payload));
        }

        if (!(v instanceof Collection)) {
            throw new ProcessException("The value of 'activeProfiles' parameter must be an array of strings");
        }

        Collection<String> c = (Collection<String>) v;
        if (c.isEmpty()) {
            return chain.process(useDefaultProfiles(payload));
        }

        String[] as = c.toArray(new String[c.size()]);
        payload = payload.putHeader(Payload.ACTIVE_PROFILES, as);
        return chain.process(payload);
    }

    private static Payload useDefaultProfiles(Payload p) {
        return p.putHeader(Payload.ACTIVE_PROFILES, DEFAULT_PROFILES);
    }
}
