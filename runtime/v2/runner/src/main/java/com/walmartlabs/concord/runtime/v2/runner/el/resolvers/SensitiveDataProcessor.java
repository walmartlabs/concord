package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

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

import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveData;

import java.lang.reflect.Method;
import java.util.*;

public class SensitiveDataProcessor {

    public static void processFirstMatch(Object value, List<Method> methods) {
        Method method = methods.stream().filter(SensitiveDataProcessor::isSensitiveData).findFirst().orElse(null);
        if (method == null) {
            return;
        }

        process(value, method);
    }

    public static void process(Object value, Method method) {
        SensitiveData a = method.getAnnotation(SensitiveData.class);
        if (a == null) {
            return;
        }

        if (value instanceof String) {
            SensitiveDataHolder.getInstance().add((String) value);
        } else if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            Set<?> keys = a.keys() != null && a.keys().length > 0 ? new HashSet<Object>(Arrays.asList(a.keys())) : m.keySet();

            for (Object key : keys) {
                Object v = m.get(key);
                if (v instanceof String) {
                    SensitiveDataHolder.getInstance().add((String) v);
                }
            }
        }
    }

    private static boolean isSensitiveData(Method method) {
        return method.getAnnotation(SensitiveData.class) != null;
    }
}
