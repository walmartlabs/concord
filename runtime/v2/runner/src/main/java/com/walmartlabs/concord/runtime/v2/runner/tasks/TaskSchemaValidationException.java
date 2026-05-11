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
import java.util.HashMap;
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
    private final String schemaResource;
    private final List<String> validationErrors;

    public TaskSchemaValidationException(String taskName, String section, List<String> validationErrors) {
        this(taskName, section, null, validationErrors);
    }

    public TaskSchemaValidationException(String taskName, String section, String schemaResource, List<String> validationErrors) {
        super(formatMessage(taskName, section, schemaResource, validationErrors), details(taskName, section, schemaResource, validationErrors));
        this.taskName = taskName;
        this.section = section;
        this.schemaResource = schemaResource;
        this.validationErrors = List.copyOf(validationErrors);
    }

    public String getTaskName() {
        return taskName;
    }

    public String getSection() {
        return section;
    }

    public String getSchemaResource() {
        return schemaResource;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    private static Map<String, Object> details(String taskName, String section, String schemaResource, List<String> errors) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskName", taskName);
        result.put("section", section);
        result.put("errors", errors);
        if (schemaResource != null) {
            result.put("schemaResource", schemaResource);
        }
        return result;
    }

    private static String formatMessage(String taskName, String section, String schemaResource, List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task '").append(taskName).append("' ").append(section).append(" validation failed");
        if (schemaResource != null) {
            sb.append(" (schema: ").append(schemaResource).append(")");
        }
        sb.append(":");
        for (String error : errors) {
            sb.append("\n  - ").append(error);
        }
        return sb.toString();
    }
}
