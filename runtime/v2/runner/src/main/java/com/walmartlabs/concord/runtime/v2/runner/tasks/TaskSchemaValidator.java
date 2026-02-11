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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates task input and output parameters against JSON schemas.
 * <p>
 * Only JSON Schema draft-07 is supported.
 */
@Named
@Singleton
public class TaskSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(TaskSchemaValidator.class);

    private final TaskSchemaRegistry registry;
    private final ObjectMapper objectMapper;

    @Inject
    public TaskSchemaValidator(TaskSchemaRegistry registry) {
        this.registry = registry;
        this.objectMapper = registry.getObjectMapper();
    }

    /**
     * Validate task input parameters.
     *
     * @param taskName  the task name
     * @param taskClass the resolved task class
     * @param input     the input parameters
     * @return validation result
     */
    public TaskSchemaValidationResult validateInput(String taskName, Class<? extends Task> taskClass, Map<String, Object> input) {
        TaskSchemaLookupResult schema = registry.getInputSchema(taskName, taskClass);
        return validate(taskName, "in", schema, input);
    }

    /**
     * Validate task output parameters.
     *
     * @param taskName  the task name
     * @param taskClass the resolved task class
     * @param output    the output parameters
     * @return validation result
     */
    public TaskSchemaValidationResult validateOutput(String taskName, Class<? extends Task> taskClass, Map<String, Object> output) {
        TaskSchemaLookupResult schema = registry.getOutputSchema(taskName, taskClass);
        return validate(taskName, "out", schema, output);
    }

    private TaskSchemaValidationResult validate(String taskName, String section, TaskSchemaLookupResult lookupResult, Map<String, Object> data) {
        if (lookupResult.status() == TaskSchemaLookupResult.Status.ABSENT) {
            return TaskSchemaValidationResult.noSchema();
        }
        if (lookupResult.status() == TaskSchemaLookupResult.Status.NO_SECTION) {
            return TaskSchemaValidationResult.skipped(lookupResult.resourceName());
        }
        if (lookupResult.status() == TaskSchemaLookupResult.Status.INVALID) {
            return TaskSchemaValidationResult.invalid(lookupResult.resourceName(), lookupResult.errors());
        }

        JsonSchema schema = lookupResult.schema();
        try {
            JsonNode dataNode = objectMapper.valueToTree(data);

            Set<ValidationMessage> errors = schema.validate(dataNode);
            if (errors.isEmpty()) {
                return TaskSchemaValidationResult.valid(lookupResult.resourceName());
            }

            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();

            log.debug("Validation errors for task '{}' {}: {}", taskName, section, errorMessages);
            return TaskSchemaValidationResult.invalid(lookupResult.resourceName(), errorMessages);
        } catch (Exception e) {
            log.warn("Failed to validate task '{}' {}: {}", taskName, section, e.getMessage());
            return TaskSchemaValidationResult.invalid(lookupResult.resourceName(), List.of("Schema validation error: " + e.getMessage()));
        }
    }
}
