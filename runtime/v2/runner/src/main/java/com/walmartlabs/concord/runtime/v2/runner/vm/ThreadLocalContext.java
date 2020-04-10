package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import java.util.concurrent.Callable;

/**
 * Thread-local {@link Context} instance.
 * Can be used to fetch the current context during execution of expressions or tasks.
 */
public class ThreadLocalContext {

    public static <T, C extends Context> T withContext(C ctx, Callable<T> callable) throws RuntimeException {
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

    public static <C extends Context> void withContext(C ctx, Closure closure) throws RuntimeException {
        set(ctx);
        try {
            closure.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clear();
        }
    }

    private static final ThreadLocal<Context> value = new ThreadLocal<>();

    public static Context get() {
        return value.get();
    }

    private static void set(Context ctx) {
        value.set(ctx);
    }

    private static void clear() {
        value.remove();
    }

    public interface Closure {

        void call() throws Exception;
    }
}
