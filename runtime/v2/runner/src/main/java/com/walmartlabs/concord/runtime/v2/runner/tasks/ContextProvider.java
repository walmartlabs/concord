package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;

import javax.inject.Provider;
import java.util.concurrent.Callable;

public class ContextProvider implements Provider<Context> {

    private static final ThreadLocal<Context> value = new ThreadLocal<>();

    public static <T, C extends Context> T withContext(C ctx, Callable<T> callable) {
        set(ctx);
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

    public static <C extends Context> void withContext(C ctx, Runnable runnable) {
        set(ctx);
        try {
            runnable.run();
        } finally {
            clear();
        }
    }

    @Override
    public Context get() {
        Context ctx = value.get();
        if (ctx == null) {
            throw new IllegalStateException("No context available. This is most likely a bug.");
        }

        return ctx;
    }

    private static void set(Context ctx) {
        value.set(ctx);
    }

    private static void clear() {
        value.remove();
    }
}
