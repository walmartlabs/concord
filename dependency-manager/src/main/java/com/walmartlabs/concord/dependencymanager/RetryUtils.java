package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

public final class RetryUtils {

    public interface RetryStrategy {
        boolean canRetry(Exception e);
    }

    public interface RetryListener {
        void onRetry(int tryCount, int retryCount, long retryInterval, Exception e);
    }

    public static <T> T withRetry(int retryCount, long retryInterval, Callable<T> c,
                                  RetryStrategy strategy, RetryListener listener) throws Exception {
        Exception exception = null;
        int tryCount = 1;
        while (!Thread.currentThread().isInterrupted() && tryCount < retryCount + 1) {
            try {
                return c.call();
            } catch (Exception e) {
                if (!strategy.canRetry(e)) {
                    throw e;
                }
                exception = e;
            }
            listener.onRetry(tryCount, retryCount, retryInterval, exception);
            sleep(retryInterval);
            tryCount++;
        }
        if (exception == null) {
            throw new InterruptedException("Retry interrupted");
        }
        throw exception;
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RetryUtils() {
    }
}
