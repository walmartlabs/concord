package com.walmartlabs.concord.runtime.v25.runner.engine;

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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface RetryScheduler {

    long MAX_DELAY_SECONDS = Long.MAX_VALUE / 1_000L;

    RetryScheduler SYSTEM = delay -> {
        validate(delay);
        return CompletableFuture.runAsync(() -> {
        }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
    };

    static void validate(Duration delay) {
        if (delay.getSeconds() > MAX_DELAY_SECONDS) {
            throw new IllegalArgumentException("Retry delay must not exceed " + MAX_DELAY_SECONDS
                    + " seconds (Long.MAX_VALUE milliseconds)");
        }
    }

    CompletionStage<Void> delay(Duration duration);
}
