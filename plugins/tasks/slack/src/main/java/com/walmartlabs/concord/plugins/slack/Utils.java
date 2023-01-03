package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Utils {

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

    public static Variables merge(Variables variables, Map<String, Object> defaults) {
        Map<String, Object> variablesMap = new HashMap<>(defaults != null ? defaults : Collections.emptyMap());
        variablesMap.putAll(variables.toMap());
        return new MapBackedVariables(variablesMap);
    }

    private Utils() {
    }
}
