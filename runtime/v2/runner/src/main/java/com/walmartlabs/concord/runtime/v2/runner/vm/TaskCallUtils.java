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
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.svm.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class TaskCallUtils {

    private static final Logger log = LoggerFactory.getLogger(TaskCallUtils.class);

    public static void processTaskResult(Runtime runtime, Context ctx, String taskName, TaskCallOptions opts, TaskResult result) {
        assertTaskResult(taskName, result);

        if (result instanceof TaskResult.SuspendResult r) {
            ctx.suspend(r.eventName());
        } else if (result instanceof TaskResult.ReentrantSuspendResult r) {
            ctx.reentrantSuspend(r.eventName(), r.payload());
        } else if (result instanceof TaskResult.SimpleResult r) {

            OutputUtils.process(runtime, ctx, toMap(ctx, r), opts.out(), opts.outExpr());

            if (r.ok()) {
                return;
            }

            if (result instanceof TaskResult.SimpleFailResult rr) {
                RuntimeException exception = toException(rr);

                if (opts.ignoreErrors()) {
                   log.error("Error ignored:", exception);
                } else {
                    throw exception;
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown result: '" + result.getClass() + "'");
        }
    }

    private static RuntimeException toException(TaskResult.SimpleFailResult rr) {
        var cause = rr.cause();
        if (cause == null) {
            return new UserDefinedException(rr.error(), rr.values());
        }

        if (cause instanceof RuntimeException re) {
            return re;
        }

        return new WrappedException(rr.cause());
    }

    private static Map<String, Object> toMap(Context ctx, TaskResult.SimpleResult result) {
        Map<String, Object> resultAsMap = result.toMap();
        resultAsMap.put("threadId", ctx.execution().currentThreadId().id());
        return resultAsMap;
    }

    private static void assertTaskResult(String taskName, TaskResult taskResult) {
        if (taskResult == null) {
            throw new RuntimeException("Task '" + taskName + "' return NULL. This is most likely a bug.");
        }
    }

    private TaskCallUtils() {
    }
}
