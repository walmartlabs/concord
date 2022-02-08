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

public final class OrDefaultFunction {

    public static Method getMethod() {
        try {
            return OrDefaultFunction.class.getMethod("orDefault", String.class, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T orDefault(String variableName, T defaultValue) {
        boolean has = HasVariableFunction.hasVariable(variableName);
        if (!has) {
            return defaultValue;
        }

        return (T) getValue(variableName);
    }

    @SuppressWarnings("unchecked")
    private static Object getValue(String variableName) {
        Variables variables = ThreadLocalEvalContext.get().variables();

        String[] path = variableName.split("\\.");
        if (path.length == 1) {
            return variables.get(variableName);
        } else {
            Object maybeMap = variables.get(path[0]);
            if (!(maybeMap instanceof Map)) {
                throw new IllegalStateException("Expected a map. This is most likely a bug");
            }
            return ConfigurationUtils.get((Map<String, Object>) maybeMap, Arrays.copyOfRange(path, 1, path.length));
        }
    }
}
