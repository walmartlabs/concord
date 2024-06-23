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

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Collections;
import java.util.UUID;

public class TaskResumeCommand extends StepCommand<TaskCall> {

    private static final long serialVersionUID = 1L;

    private final ResumeEvent event;

    protected TaskResumeCommand(UUID correlationId, TaskCall step, ResumeEvent event) {
        super(correlationId, step);

        this.event = event;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Context ctx = runtime.getService(Context.class);

        String taskName = getStep().getName();

        TaskProviders taskProviders = runtime.getService(TaskProviders.class);
        Task task = taskProviders.createTask(ctx, getStep().getName());
        if (task == null) {
            throw new IllegalStateException("Task not found: " + taskName);
        }

        if (!(task instanceof ReentrantTask rt)) {
            throw new IllegalStateException("The task doesn't implement the " + ReentrantTask.class.getSimpleName() +
                    " interface and cannot be used as a \"reentrant\" task: " + taskName);
        }

        TaskCallInterceptor.CallContext callContext = TaskCallInterceptor.CallContext.builder()
                .threadId(threadId)
                .taskName(taskName)
                .correlationId(ctx.execution().correlationId())
                .currentStep(getStep())
                .processDefinition(ctx.execution().processDefinition())
                .build();

        TaskCallInterceptor interceptor = runtime.getService(TaskCallInterceptor.class);

        TaskResult result;
        try {
            result = interceptor.invoke(callContext, TaskCallInterceptor.Method.of(rt, "resume", Collections.singletonList(event)),
                    () -> rt.resume(event));
        } catch (TaskException e) {
            result = TaskResult.fail(e.getCause());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TaskCallUtils.processTaskResult(runtime, ctx, taskName, getStep().getOptions(), result);
    }
}
