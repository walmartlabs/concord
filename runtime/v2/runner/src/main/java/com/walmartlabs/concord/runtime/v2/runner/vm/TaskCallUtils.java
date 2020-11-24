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
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.svm.Runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public final class TaskCallUtils {

    public static void processTaskResult(Runtime runtime, Context ctx, String taskName, TaskCallOptions opts, TaskResult result) {
        assertTaskResult(taskName, result);

        if (result instanceof TaskResult.SuspendResult) {
            TaskResult.SuspendResult r = (TaskResult.SuspendResult) result;
            ctx.suspend(r.eventName());
        } else if (result instanceof TaskResult.ReentrantSuspendResult) {
            TaskResult.ReentrantSuspendResult r = (TaskResult.ReentrantSuspendResult) result;
            ctx.reentrantSuspend(r.eventName(), r.payload());
        } else if (result instanceof TaskResult.SimpleResult) {
            TaskResult.SimpleResult r = (TaskResult.SimpleResult) result;
            if (opts.out() != null) {
                ctx.variables().set(opts.out(), toMap(ctx, r));
            } else if (opts.outExpr() != null) {
                ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
                Map<String, Object> vars = Collections.singletonMap("result", toMap(ctx, r));
                Map<String, Serializable> out = expressionEvaluator.evalAsMap(EvalContextFactory.global(ctx, vars), opts.outExpr());
                out.forEach((k, v) -> ctx.variables().set(k, v));
            }
        } else {
            throw new IllegalArgumentException("Unknown result: '" + result.getClass() + "'");
        }
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
