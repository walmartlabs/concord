package com.walmartlabs.concord.runtime.v2.runner.script;

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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.NashornUtils.*;

public final class VariablesSanitizer {

    @SuppressWarnings("unchecked")
    public static Object sanitize(Object scriptObj) {
        if (isNashornScriptObjectMirror(scriptObj)) {
            if (isNashornArray(scriptObj)) {
                // turn JS (Nashorn) arrays into Java Lists
                return getNashornObjectEntrySet(scriptObj).
                        stream()
                        .map(entry -> sanitize(entry.getValue()))
                        .collect(Collectors.toList());
            } else {
                // turn other JS (Nashorn) objects into Java Maps
                return getNashornObjectEntrySet(scriptObj)
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> sanitize(entry.getValue()), (a, b) -> b));
            }
        } else if (scriptObj instanceof Set) {
            Set<Object> c = (Set<Object>) scriptObj;
            return c.stream()
                    .map(VariablesSanitizer::sanitize)
                    .collect(Collectors.toSet());
        } else if (scriptObj instanceof Collection) {
            Collection<Object> c = (Collection<Object>) scriptObj;
            return c.stream()
                    .map(VariablesSanitizer::sanitize)
                    .collect(Collectors.toList());
        } else if (scriptObj instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) scriptObj;
            Map<Object, Object> result = new HashMap<>();
            m.forEach((key, value) -> result.put(sanitize(key), sanitize(value)));
            return result;
        } else {
            return scriptObj;
        }
    }

    private VariablesSanitizer() {
    }
}
