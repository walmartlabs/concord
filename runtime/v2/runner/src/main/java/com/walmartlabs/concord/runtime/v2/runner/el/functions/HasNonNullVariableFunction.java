package com.walmartlabs.concord.runtime.v2.runner.el.functions;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public final class HasNonNullVariableFunction {

    public static Method getMethod() {
        try {
            return HasNonNullVariableFunction.class.getMethod("hasVariable", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean hasVariable(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        Variables variables = ThreadLocalEvalContext.get().variables();

        Object value = null;
        String[] path = name.split("\\.");
        if (path.length == 1) {
            value = variables.get(name);
        } else {
            Object maybeMap = variables.get(path[0]);
            if (!(maybeMap instanceof Map)) {
                return false;
            }
            String[] p = Arrays.copyOfRange(path, 1, path.length);
            if (ConfigurationUtils.has((Map<String, Object>)maybeMap, p)) {
                value = ConfigurationUtils.get((Map<String, Object>)maybeMap, p);
            }
        }
        return value != null;
    }
}
