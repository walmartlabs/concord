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

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;

import java.util.List;

/**
 * Result of looking up and compiling one section of a task schema resource.
 *
 * @param status       lookup status
 * @param schema       compiled JSON schema when {@link Status#FOUND}
 * @param rawSchema    raw task schema resource, when available
 * @param resourceName schema resource name
 * @param errors       lookup or compile errors when {@link Status#INVALID}
 */
public record TaskSchemaLookupResult(
        Status status,
        JsonSchema schema,
        JsonNode rawSchema,
        String resourceName,
        List<String> errors
) {

    public enum Status {
        /**
         * No task schema resource exists.
         */
        ABSENT,
        /**
         * Schema resource exists, but the requested top-level section is not declared.
         */
        NO_SECTION,
        /**
         * Schema resource or requested section exists but cannot be parsed or compiled.
         */
        INVALID,
        /**
         * Requested section was found and compiled.
         */
        FOUND
    }

    public TaskSchemaLookupResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public boolean hasErrors() {
        return status == Status.INVALID && !errors.isEmpty();
    }

    public static TaskSchemaLookupResult absent(String resourceName) {
        return new TaskSchemaLookupResult(Status.ABSENT, null, null, resourceName, List.of());
    }

    public static TaskSchemaLookupResult noSection(JsonNode rawSchema, String resourceName) {
        return new TaskSchemaLookupResult(Status.NO_SECTION, null, rawSchema, resourceName, List.of());
    }

    public static TaskSchemaLookupResult invalid(JsonNode rawSchema, String resourceName, List<String> errors) {
        return new TaskSchemaLookupResult(Status.INVALID, null, rawSchema, resourceName, errors);
    }

    public static TaskSchemaLookupResult found(JsonSchema schema, JsonNode rawSchema, String resourceName) {
        return new TaskSchemaLookupResult(Status.FOUND, schema, rawSchema, resourceName, List.of());
    }
}
