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

import com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext;

import java.lang.reflect.Method;
import java.util.Map;

public final class AllVariablesFunction {

    public static Method getMethod() {
        try {
            return AllVariablesFunction.class.getMethod("allVariables");
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    public static Map<String, Object> allVariables() {
        return ThreadLocalEvalContext.get().context().globalVariables().toMap();
    }
}
