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

import com.google.inject.Inject;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

public class SensitiveDataProcessor {

    private static final int MAX_DEPTH = 10;

    private final SensitiveDataHolder sensitiveDataHolder;

    @Inject
    public SensitiveDataProcessor(SensitiveDataHolder sensitiveDataHolder) {
        this.sensitiveDataHolder = sensitiveDataHolder;
    }

    @SuppressWarnings("unchecked")
    public void process(Object value, Method method) {
        if (value == null || method == null) {
            return;
        }

        var a = findSensitiveDataAnnotation(method);
        if (a == null) {
            return;
        }

        if (value instanceof String) {
            sensitiveDataHolder.add((String) value);
        }

        if (value instanceof Map<?, ?> m) {
            collectFromMap((Map<String, Object>) m, a);
        }
    }

    private void collectFromMap(Map<String, Object> m, SensitiveData a) {
        if (a.keys() != null && a.keys().length > 0) {
            for (var k : a.keys()) {
                var path = k.split("\\.");
                if (ConfigurationUtils.has(m, path)) {
                    var v = ConfigurationUtils.get(m, path);
                    collectValue(v, a);
                }
            }
        } else {
            for (var key : m.keySet()) {
                collectValue(m.get(key), a);
            }
        }
    }

    private void collectValue(Object v, SensitiveData a) {
        if (v instanceof String s) {
            sensitiveDataHolder.add(s);
            return;
        }

        if (a.includeNestedValues() && v instanceof Map<?, ?> nested) {
            processNestedValues(nested, 0);
        }
    }

    private void processNestedValues(Object obj, int depth) {
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
            sensitiveDataHolder.add(str);
        }
    }

    private SensitiveData findSensitiveDataAnnotation(Method method) {
        if (method.isBridge()) {
            return findAnnotationFromBridgedMethod(method);
        }
        return method.getAnnotation(SensitiveData.class);
    }

    /**
     * This is a simple bridge resolution mechanism suitable for sensitive data processing.
     */
    private static SensitiveData findAnnotationFromBridgedMethod(Method bridgeMethod) {
        var declaringClass = bridgeMethod.getDeclaringClass();
        for (var candidate : declaringClass.getDeclaredMethods()) {
            if (isBridgedCandidateFor(bridgeMethod, candidate)) {
                var result = candidate.getAnnotation(SensitiveData.class);
                if (result != null) {
                    return result;
                }
            }
        }
        return bridgeMethod.getAnnotation(SensitiveData.class);
    }

    private static boolean isBridgedCandidateFor(Method bridge, Method candidate) {
        return !candidate.isBridge() &&
                !candidate.equals(bridge) &&
                candidate.getName().equals(bridge.getName()) &&
                candidate.getParameterCount() == bridge.getParameterCount();
    }
}
