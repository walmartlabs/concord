package com.walmartlabs.concord.plugins.slack;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Utils {

    public static Map<String, Object> collectAgs(Context ctx) {
        Map<String, Object> defaults = ContextUtils.getMap(ctx, "slackCfg", Collections.emptyMap());

        Map<String, Object> m = new HashMap<>(defaults);
        for (TaskParams p : TaskParams.values()) {
            String k = p.getKey();
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    public static String extractString(SlackClient.Response r, String... path) {
        Map<String, Object> m = r.getParams();
        if (m == null) {
            return null;
        }

        int idx = 0;
        while (true) {
            String s = path[idx];

            Object v = m.get(s);
            if (v == null) {
                return null;
            }

            if (idx + 1 >= path.length) {
                if (v instanceof String) {
                    return (String) v;
                } else {
                    throw new IllegalStateException("Expected a string value @ " + Arrays.toString(path) + ", got: " + v);
                }
            }

            if (!(v instanceof Map)) {
                throw new IllegalStateException("Expected a JSON object, got: " + v);
            }
            m = (Map<String, Object>) v;

            idx += 1;
        }
    }

    private Utils() {
    }
}
