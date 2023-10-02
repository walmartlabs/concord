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

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public final class VariablesSanitizer {

    @SuppressWarnings("unchecked")
    public static Object sanitize(Object obj) {
        if (obj instanceof Set) {
            Set<Object> c = (Set<Object>) obj;
            return c.stream()
                    .map(VariablesSanitizer::sanitize)
                    .collect(Collectors.toSet());
        } else if (obj instanceof Collection) {
            Collection<Object> c = (Collection<Object>) obj;
            return c.stream()
                    .map(VariablesSanitizer::sanitize)
                    .collect(Collectors.toList());
        } else if (obj instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) obj;
            Map<Object, Object> result = new LinkedHashMap<>();
            m.forEach((key, value) -> result.put(sanitize(key), sanitize(value)));
            return result;
        } else if (obj instanceof Map.Entry && !(obj instanceof Serializable)) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) obj;
            return new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue());
        } else {
            return obj;
        }
    }

    private VariablesSanitizer() {
    }
}
