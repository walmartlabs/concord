package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

public class Utils {
    public static final int maxLevel = 20;

    public static void recursionLimiter() {
        if (maxLevel == 0)
            return;
        try {
            throw new IllegalStateException("Too deep, recursion limit reached, emerging");
        } catch (IllegalStateException e) {
            StackTraceElement[] stackTraces = e.getStackTrace();
            if (stackTraces.length > 1) {
                StackTraceElement recursiveElement = stackTraces[1];
                int depth = 1;
                for (; depth < stackTraces.length; depth++) {
                    if (!(stackTraces[depth].getClassName().equals(recursiveElement.getClassName())
                            && stackTraces[depth].getMethodName().equals(recursiveElement.getMethodName()))) {
                        break;
                    }
                }
                if (depth > maxLevel + 1)
                    throw e;
            }
        }
    }
}
