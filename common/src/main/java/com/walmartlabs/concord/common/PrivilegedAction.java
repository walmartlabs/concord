package com.walmartlabs.concord.common;

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

import java.io.IOException;

public final class PrivilegedAction {

    private static final ThreadLocal<String> currentDomain = new ThreadLocal<>();

    public static String getCurrentDomain() {
        return currentDomain.get();
    }

    public static <T> T perform(String domain, IOAction<T> f) throws IOException {
        String prevDomain = currentDomain.get();
        try {
            currentDomain.set(domain);
            return f.call();
        } finally {
            if (prevDomain == null) {
                currentDomain.remove();
            } else {
                currentDomain.set(prevDomain);
            }
        }
    }

    public interface IOAction<T> {

        T call() throws IOException;
    }

    private PrivilegedAction() {
    }
}
