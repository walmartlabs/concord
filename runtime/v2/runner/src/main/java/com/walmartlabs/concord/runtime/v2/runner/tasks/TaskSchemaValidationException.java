package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when task schema validation fails in FAIL mode.
 */
public class TaskSchemaValidationException extends UserDefinedException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String taskName;
    private final String section;
    private final List<String> validationErrors;

    public TaskSchemaValidationException(String taskName, String section, List<String> validationErrors) {
        super(formatMessage(taskName, section, validationErrors),
                Map.of("taskName", taskName, "section", section, "errors", validationErrors));
        this.taskName = taskName;
        this.section = section;
        this.validationErrors = List.copyOf(validationErrors);
    }

    public String getTaskName() {
        return taskName;
    }

    public String getSection() {
        return section;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    private static String formatMessage(String taskName, String section, List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task '").append(taskName).append("' ").append(section).append(" validation failed:");
        for (String error : errors) {
            sb.append("\n  - ").append(error);
        }
        return sb.toString();
    }
}
