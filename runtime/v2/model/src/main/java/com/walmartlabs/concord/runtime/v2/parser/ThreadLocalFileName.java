package com.walmartlabs.concord.runtime.v2.parser;

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

import java.util.concurrent.Callable;

public final class ThreadLocalFileName {

    public static <T> T withFileName(String fileName, Callable<T> callable) throws RuntimeException {
        set(fileName);
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clear();
        }
    }

    private static final ThreadLocal<String> value = new ThreadLocal<>();

    public static String get() {
        return value.get();
    }

    private static void set(String ctx) {
        value.set(ctx);
    }

    private static void clear() {
        value.remove();
    }

    private ThreadLocalFileName() {
    }
}
