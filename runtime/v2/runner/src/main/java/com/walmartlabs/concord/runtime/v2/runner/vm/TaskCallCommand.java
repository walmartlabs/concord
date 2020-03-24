package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.context.TaskContextImpl;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.Interpolator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the specified task. Responsible for preparing the task's input
 * and processing the output.
 */
public class TaskCallCommand extends StepCommand<TaskCall> {

    private static final long serialVersionUID = 1L;

    public TaskCallCommand(TaskCall step) {
        super(step);
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        TaskProviders taskProviders = runtime.getService(TaskProviders.class);
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        TaskCall call = getStep();
        String taskName = call.getName();
        Task t = taskProviders.createTask(ctx, taskName);
        if (t == null) {
            throw new IllegalStateException("Task not found: " + taskName);
        }

        TaskCallOptions opts = call.getOptions();
        Map<String, Object> input = prepareInput(expressionEvaluator, ctx, opts);

        Serializable result;
        ThreadLocalContext.set(ctx);
        try {
            result = t.execute(new TaskContextImpl(ctx, taskName, input));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            ThreadLocalContext.clear();
        }

        String out = opts.out();
        if (out != null) {
            GlobalVariables gv = runtime.getService(GlobalVariables.class);
            gv.put(out, result); // TODO a custom result structure
        }
    }

    /**
     * Combines the task input and the frame-local task input overrides.
     * I.e. {@code retry} or a similar mechanism can produce an updated
     * set of {@code in} variables which should override the original
     * {@code input}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> prepareInput(ExpressionEvaluator ee,
                                                    Context ctx,
                                                    TaskCallOptions opts) {

        Map<String, Serializable> input = opts.input();
        if (input == null) {
            input = Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>(input);

        Map<String, Object> frameOverrides = VMUtils.getTaskInputOverrides(ctx);
        result.putAll(frameOverrides);

        result = new HashMap<String, Object>(Interpolator.interpolate(ee, ctx, result, Map.class));
        return Collections.unmodifiableMap(result);
    }
}