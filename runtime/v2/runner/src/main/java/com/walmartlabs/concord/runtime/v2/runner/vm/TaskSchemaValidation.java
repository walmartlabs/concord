package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation;
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation.ValidationMode;
import com.walmartlabs.concord.runtime.v2.model.ValidationConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidationException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidationResult;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.svm.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

final class TaskSchemaValidation {

    private static final Logger log = LoggerFactory.getLogger(TaskSchemaValidation.class);

    static Class<? extends Task> getTaskClass(TaskProviders taskProviders, Context ctx, Task task, String taskName) {
        Class<? extends Task> taskClass = taskProviders.getTaskClass(ctx, taskName);
        if (taskClass != null) {
            return taskClass;
        }
        return task.getClass();
    }

    static void validateInput(Runtime runtime,
                              String taskName,
                              Class<? extends Task> taskClass,
                              Variables input,
                              TaskCallValidation validation) {
        if (validation.in() == ValidationMode.DISABLED) {
            return;
        }

        TaskSchemaValidator validator = runtime.getService(TaskSchemaValidator.class);
        TaskSchemaValidationResult result = validator.validateInput(taskName, taskClass, input.toMap());
        handleValidationResult(taskName, "in", result, validation.in());
    }

    static void validateOutput(Runtime runtime,
                               String taskName,
                               Class<? extends Task> taskClass,
                               TaskResult result,
                               TaskCallValidation validation) {
        if (result == null) {
            return;
        }

        if (validation.out() == ValidationMode.DISABLED) {
            return;
        }

        if (!(result instanceof TaskResult.SimpleResult simpleResult)) {
            String resultType = result.getClass().getSimpleName();
            if (validation.out() == ValidationMode.WARN) {
                log.warn("Task '{}' output validation enabled but result type '{}' does not expose output values", taskName, resultType);
            } else {
                log.debug("Task '{}' output validation skipped for result type '{}'", taskName, resultType);
            }
            return;
        }

        // Failed results are skipped so schema errors do not mask the task's original failure.
        if (!simpleResult.ok()) {
            log.debug("Task '{}' output validation skipped for failed result", taskName);
            return;
        }

        TaskSchemaValidator validator = runtime.getService(TaskSchemaValidator.class);
        TaskSchemaValidationResult validationResult = validator.validateOutput(taskName, taskClass, simpleResult.toMap());
        handleValidationResult(taskName, "out", validationResult, validation.out());
    }

    /**
     * Get the task call validation configuration, handling null for backward compatibility
     * with old serialized process definitions that don't have the validation field.
     */
    static TaskCallValidation getTaskCallValidation(Context ctx) {
        ValidationConfiguration cfg = ctx.execution().processDefinition().configuration().validation();
        if (cfg == null) {
            return new TaskCallValidation();
        }

        TaskCallValidation validation = cfg.taskCalls();
        if (validation == null) {
            return new TaskCallValidation();
        }

        return validation;
    }

    private static void handleValidationResult(String taskName,
                                               String section,
                                               TaskSchemaValidationResult result,
                                               ValidationMode mode) {
        if (!result.hasErrors()) {
            return;
        }

        if (mode == ValidationMode.WARN) {
            log.warn("Task '{}' {} validation errors{}:{}", taskName, section, schemaResourceSuffix(result), formatErrors(result.errors()));
        } else if (mode == ValidationMode.FAIL) {
            throw new TaskSchemaValidationException(taskName, section, result.schemaResource(), result.errors());
        }
    }

    private static String schemaResourceSuffix(TaskSchemaValidationResult result) {
        if (result.schemaResource() == null) {
            return "";
        }
        return " (" + result.schemaResource() + ")";
    }

    private static String formatErrors(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            sb.append("\n  - ").append(error);
        }
        return sb.toString();
    }

    private TaskSchemaValidation() {
    }
}
