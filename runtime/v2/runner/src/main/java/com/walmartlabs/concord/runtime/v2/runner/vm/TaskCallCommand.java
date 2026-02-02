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
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation;
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation.ValidationMode;
import com.walmartlabs.concord.runtime.v2.model.ValidationConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.SensitiveDataProcessor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.*;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(TaskCallCommand.class);

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

        // Input validation (null-safe for backward compatibility with old serialized state)
        TaskCallValidation validation = getTaskCallValidation(ctx);
        if (validation.in() != ValidationMode.DISABLED) {
            TaskSchemaValidator validator = runtime.getService(TaskSchemaValidator.class);
            TaskSchemaValidationResult validationResult = validator.validateInput(taskName, input.toMap());
            handleValidationResult(taskName, "in", validationResult, validation.in());
        }

        TaskResult result;
        try {
            result = interceptor.invoke(callContext, Method.of(t.getClass(), "execute", Collections.singletonList(input)),
                    () -> t.execute(input));

            if (result instanceof TaskResult.SimpleResult simpleResult) {
                var m = ReflectionUtil.findMethod(t.getClass(), "execute", new Class[]{Variables.class}, new Variables[]{input});
                runtime.getService(SensitiveDataProcessor.class).process(simpleResult.values(), m);

                // Output validation
                if (validation.out() != ValidationMode.DISABLED) {
                    TaskSchemaValidator validator = runtime.getService(TaskSchemaValidator.class);
                    TaskSchemaValidationResult validationResult = validator.validateOutput(taskName, simpleResult.toMap());
                    handleValidationResult(taskName, "out", validationResult, validation.out());
                }
            } else if (validation.out() != ValidationMode.DISABLED) {
                log.warn("Task '{}' output validation enabled but result type '{}' does not support validation",
                        taskName, result.getClass().getSimpleName());
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

    private static void handleValidationResult(String taskName, String section,
                                               TaskSchemaValidationResult result, ValidationMode mode) {
        if (!result.hasErrors()) {
            return;
        }

        if (mode == ValidationMode.WARN) {
            log.warn("Task '{}' {} validation errors:{}", taskName, section, formatErrors(result.errors()));
        } else if (mode == ValidationMode.FAIL) {
            throw new TaskSchemaValidationException(taskName, section, result.errors());
        }
    }

    private static String formatErrors(java.util.List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            sb.append("\n  - ").append(error);
        }
        return sb.toString();
    }

    /**
     * Get the task call validation configuration, handling null for backward compatibility
     * with old serialized process definitions that don't have the validation field.
     */
    private static TaskCallValidation getTaskCallValidation(Context ctx) {
        ValidationConfiguration validationConfig = ctx.execution().processDefinition().configuration().validation();
        if (validationConfig == null) {
            return new TaskCallValidation();
        }
        TaskCallValidation taskCalls = validationConfig.taskCalls();
        if (taskCalls == null) {
            return new TaskCallValidation();
        }
        return taskCalls;
    }
}
