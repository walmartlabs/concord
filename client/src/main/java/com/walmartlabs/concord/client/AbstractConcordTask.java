package com.walmartlabs.concord.client;

import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.client.Keys.BASEURL_KEY;
import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

public abstract class AbstractConcordTask implements Task {

    @Inject
    ApiConfiguration apiCfg;

    protected Map<String, Object> createCfg(Context ctx) {
        return createCfg(ctx, (String[]) null);
    }

    protected Map<String, Object> createCfg(Context ctx, String ... keys) {
        Map<String, Object> m = new HashMap<>();

        String baseUrl = apiCfg.getBaseUrl();
        if (baseUrl != null) {
            m.put(BASEURL_KEY, baseUrl);
        }

        String sessionToken = apiCfg.getSessionToken(ctx);
        if (sessionToken != null) {
            m.put(SESSION_TOKEN_KEY, sessionToken);
        }

        for (String k : keys) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    protected static <T> T get(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) {
            throw new IllegalArgumentException("'" + k + "' is required");
        }
        return (T) v;
    }
}
