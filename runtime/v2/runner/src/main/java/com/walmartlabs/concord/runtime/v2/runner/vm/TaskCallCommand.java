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

import com.sun.el.util.ReflectionUtil;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.SensitiveDataProcessor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.CallContext;
import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;

/**
 * Calls the specified task. Responsible for preparing the task's input
 * and processing the output.
 */
public class TaskCallCommand extends StepCommand<TaskCall> {

    private static final long serialVersionUID = 1L;

    public TaskCallCommand(UUID correlationId, TaskCall step) {
        super(correlationId, step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Context ctx = runtime.getService(Context.class);

        TaskProviders taskProviders = runtime.getService(TaskProviders.class);
        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        TaskCall call = getStep();
        String taskName = call.getName();
        Task t = taskProviders.createTask(ctx, taskName);
        if (t == null) {
            throw new UserDefinedException("Task not found: '" + taskName + "'");
        }

        TaskCallInterceptor interceptor = runtime.getService(TaskCallInterceptor.class);

        CallContext callContext = CallContext.builder()
                .threadId(threadId)
                .taskName(taskName)
                .correlationId(ctx.execution().correlationId())
                .currentStep(getStep())
                .processDefinition(ctx.execution().processDefinition())
                .build();

        TaskCallOptions opts = Objects.requireNonNull(call.getOptions());
        Variables input = new MapBackedVariables(VMUtils.prepareInput(ecf, expressionEvaluator, ctx, opts.input(), opts.inputExpression()));

        TaskResult result;
        try {
            result = interceptor.invoke(callContext, Method.of(t.getClass(), "execute", Collections.singletonList(input)),
                    () -> t.execute(input));

            if (result instanceof TaskResult.SimpleResult simpleResult) {
                var m = ReflectionUtil.findMethod(t.getClass(), "execute", new Class[]{Variables.class}, new Variables[]{input});
                runtime.getService(SensitiveDataProcessor.class).process(simpleResult.values(), m);
            }
        } catch (TaskException e) {
            result = TaskResult.fail(e.getCause());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TaskCallUtils.processTaskResult(runtime, ctx, taskName, opts, result);
    }
}
