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

import java.util.List;

/**
 * Result of task schema validation.
 *
 * @param status the validation status
 * @param errors list of validation error messages
 */
public record TaskSchemaValidationResult(
        Status status,
        List<String> errors
) {

    public enum Status {
        /**
         * No schema found for task
         **/
        NO_SCHEMA,
        /**
         * Schema found but section (in/out) not defined
         **/
        SKIPPED,
        /**
         * Validation passed
         **/
        VALID,
        /**
         * Validation failed with errors
         **/
        INVALID
    }

    public TaskSchemaValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public boolean hasErrors() {
        return status == Status.INVALID && !errors.isEmpty();
    }

    public static TaskSchemaValidationResult noSchema() {
        return new TaskSchemaValidationResult(Status.NO_SCHEMA, List.of());
    }

    public static TaskSchemaValidationResult skipped() {
        return new TaskSchemaValidationResult(Status.SKIPPED, List.of());
    }

    public static TaskSchemaValidationResult valid() {
        return new TaskSchemaValidationResult(Status.VALID, List.of());
    }

    public static TaskSchemaValidationResult invalid(List<String> errors) {
        return new TaskSchemaValidationResult(Status.INVALID, errors);
    }
}
