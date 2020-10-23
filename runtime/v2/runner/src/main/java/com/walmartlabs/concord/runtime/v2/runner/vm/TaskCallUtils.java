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

import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;

public final class TaskCallUtils {

    public static void processTaskResult(String taskName, TaskResult result, TaskCallOptions opts, Context ctx) {
        assertTaskResult(taskName, result);

        if (result.suspendAction() != null) {
            switch (result.suspendAction()) {
                case SUSPEND:
                    ctx.suspend(result.suspendEvent());
                    break;
                case SUSPEND_RESUME:
                    ctx.suspendResume(result.suspendEvent(), result.suspendState());
                default:
                    throw new IllegalArgumentException("Unknown suspend action: '" + result.suspendAction() + "'");
            }
            return;
        }

        String out = opts.out();
        if (out != null) {
            ctx.variables().set(out, result.toMap());
        }
    }

    private static void assertTaskResult(String taskName, TaskResult taskResult) {
        if (taskResult == null) {
            throw new RuntimeException("Task '" + taskName + "' return NULL. This is most likely a bug.");
        }
    }

    private TaskCallUtils() {
    }
}
