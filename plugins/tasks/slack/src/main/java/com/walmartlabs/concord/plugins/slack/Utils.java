package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
