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

import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;

import java.lang.reflect.Method;

public final class ThrowFunction {

    public static Method getMethod() {
        try {
            return ThrowFunction.class.getMethod("throwError", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    public static Object throwError(String message) {
        throw new UserDefinedException(message);
    }
}
