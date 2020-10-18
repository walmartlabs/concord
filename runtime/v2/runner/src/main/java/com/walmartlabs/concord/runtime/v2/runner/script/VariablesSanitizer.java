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

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.*;
import java.util.stream.Collectors;

public final class VariablesSanitizer {

    @SuppressWarnings("unchecked")
    public static Object sanitize(Object scriptObj) {
        if (scriptObj instanceof ScriptObjectMirror) {
            ScriptObjectMirror scriptObjectMirror = (ScriptObjectMirror) scriptObj;
            if (scriptObjectMirror.isArray()) {
                List<Object> result = new ArrayList<>();
                for (Map.Entry<String, Object> entry : scriptObjectMirror.entrySet()) {
                    result.add(sanitize(entry.getValue()));
                }
                return result;
            } else {
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : scriptObjectMirror.entrySet()) {
                    result.put(entry.getKey(), sanitize(entry.getValue()));
                }
                return result;
            }
        } else if (scriptObj instanceof Collection) {
            Collection<Object> c = (Collection<Object>) scriptObj;
            return c.stream().map(VariablesSanitizer::sanitize)
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
