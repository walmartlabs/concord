package com.walmartlabs.concord.runtime.v25.runner.task;

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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvocationExecutorTest {

    @Test
    void nestedParallelChildrenReuseTheSubmittingWorkerAdmission() {
        try (var executor = new InvocationExecutor(1, Duration.ofSeconds(1))) {
            var result = InvocationExecutor.withCurrent(executor, () -> executor.call(() -> {
                var first = InvocationExecutor.submitCurrent(() -> InvocationExecutor.callCurrent(() -> "first"));
                var second = InvocationExecutor.submitCurrent(() -> InvocationExecutor.callCurrent(() -> "second"));
                return first.get() + ":" + second.get();
            }));

            assertEquals("first:second", result);
        }
    }

    @Test
    void nestedParallelDescendantsLoanTheOnlyWorkerAdmission() {
        try (var executor = new InvocationExecutor(1, Duration.ofSeconds(1))) {
            var result = InvocationExecutor.withCurrent(executor, () -> executor.call(() -> {
                var child = InvocationExecutor.submitCurrent(() -> {
                    var grandchild = InvocationExecutor.submitCurrent(() -> "grandchild");
                    return "child:" + grandchild.get();
                });
                return child.get();
            }));

            assertEquals("child:grandchild", result);
        }
    }
}
