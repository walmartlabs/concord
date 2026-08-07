package com.walmartlabs.concord.runtime.v25.runner;

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

import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;

import java.util.ArrayDeque;
import java.util.Deque;

/** Records task calls through the same filtered and batched remote event transport as engine events. */
final class RunnerTaskEventHook implements TaskRuntime.TaskHook {

    private final RunnerCallback callback;

    private final ThreadLocal<Deque<V25LogEncoder.SegmentScope>> segments = ThreadLocal.withInitial(ArrayDeque::new);

    RunnerTaskEventHook(RunnerCallback callback) {
        this.callback = callback;
    }

    @Override
    public void before(TaskRuntime.Invocation invocation) {
        V25TaskOutput.enter();
        try {
            segments.get().push(callback.enterTask(invocation));
        } catch (RuntimeException e) {
            V25TaskOutput.leave();
            throw e;
        }
    }

    @Override
    public void after(TaskRuntime.Invocation invocation, Object result, Throwable failure) {
        try {
            callback.onTask(invocation, result, failure);
        } finally {
            var scoped = segments.get();
            var segment = scoped.isEmpty() ? null : scoped.pop();
            leaveTaskOutput(segment);
            if (scoped.isEmpty()) {
                segments.remove();
            }
        }
    }

    static void leaveTaskOutput(V25LogEncoder.SegmentScope segment) {
        try {
            V25TaskOutput.leave();
        } finally {
            if (segment != null) {
                segment.close();
            }
        }
    }
}