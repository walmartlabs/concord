package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

public class SensitiveDataProcessor {

    private static final int MAX_DEPTH = 10;

    public static void processFirstMatch(Object value, List<Method> methods) {
        var method = methods.stream().filter(SensitiveDataProcessor::isSensitiveData).findFirst().orElse(null);
        if (method == null) {
            return;
        }

        process(value, method);
    }

    public static void process(Object value, Method method) {
        var a = method.getAnnotation(SensitiveData.class);
        if (a == null) {
            return;
        }

        if (value instanceof String) {
            SensitiveDataHolder.getInstance().add((String) value);
        } else if (value instanceof Map<?, ?> m) {
            var keys = a.keys() != null && a.keys().length > 0 ? new HashSet<Object>(Arrays.asList(a.keys())) : m.keySet();

            for (var key : keys) {
                var v = m.get(key);
                if (v instanceof String) {
                    SensitiveDataHolder.getInstance().add((String) v);
                } else if (a.includeNestedValues() && v instanceof Map<?,?> nested) {
                    processNestedValues(nested, 0);
                }
            }
        }
    }

    private static void processNestedValues(Object obj, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }

        if (obj instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                processNestedValues(entry.getValue(), depth + 1);
            }
        } else if (obj instanceof List<?> list) {
            for (var o : list) {
                processNestedValues(o, depth + 1);
            }
        } else if (obj instanceof Set<?> set) {
            for (var item : set) {
                processNestedValues(item, depth + 1);
            }
        } else if (obj != null && obj.getClass().isArray()) {
            var len = Array.getLength(obj);
            for (var i = 0; i < len; i++) {
                var item = Array.get(obj, i);
                processNestedValues(item, depth + 1);
            }
        } else if (obj instanceof String str) {
            SensitiveDataHolder.getInstance().add(str);
        }
    }

    private static boolean isSensitiveData(Method method) {
        return method.getAnnotation(SensitiveData.class) != null;
    }
}
