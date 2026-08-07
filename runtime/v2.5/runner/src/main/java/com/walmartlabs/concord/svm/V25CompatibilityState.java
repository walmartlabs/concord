package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.lang.reflect.Proxy;

/** Creates the minimal SVM state view required by runtime-v2 SDK tasks. */
public final class V25CompatibilityState {

    private static final ThreadId ROOT_THREAD_ID = new ThreadId(0);

    public static State create() {
        return (State) Proxy.newProxyInstance(State.class.getClassLoader(), new Class<?>[]{State.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getRootThreadId") && method.getParameterCount() == 0) {
                        return ROOT_THREAD_ID;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "runtime-v2.5 compatibility state";
                            default -> throw unsupported(method.getName());
                        };
                    }
                    throw unsupported(method.getName());
                });
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("SVM State." + operation
                + " is not supported by runtime-v2.5");
    }

    private V25CompatibilityState() {
    }
}
