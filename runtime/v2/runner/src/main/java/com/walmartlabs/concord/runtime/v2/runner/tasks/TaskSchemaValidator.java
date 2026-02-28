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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * @param taskName the task name
     * @param input    the input parameters
     * @return validation result
     */
    public TaskSchemaValidationResult validateInput(String taskName, Map<String, Object> input) {
        Optional<JsonSchema> schema = registry.getInputSchema(taskName);
        if (schema.isEmpty()) {
            return TaskSchemaValidationResult.noSchema();
        }
        return validate(taskName, "in", schema.get(), input);
    }

    /**
     * Validate task output parameters.
     *
     * @param taskName the task name
     * @param output   the output parameters
     * @return validation result
     */
    public TaskSchemaValidationResult validateOutput(String taskName, Map<String, Object> output) {
        Optional<JsonSchema> schema = registry.getOutputSchema(taskName);
        if (schema.isEmpty()) {
            return TaskSchemaValidationResult.noSchema();
        }
        return validate(taskName, "out", schema.get(), output);
    }

    private TaskSchemaValidationResult validate(String taskName, String section, JsonSchema schema, Map<String, Object> data) {
        try {
            JsonNode dataNode = objectMapper.valueToTree(data);

            Set<ValidationMessage> errors = schema.validate(dataNode);
            if (errors.isEmpty()) {
                return TaskSchemaValidationResult.valid();
            }

            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();

            log.debug("Validation errors for task '{}' {}: {}", taskName, section, errorMessages);
            return TaskSchemaValidationResult.invalid(errorMessages);
        } catch (Exception e) {
            log.warn("Failed to validate task '{}' {}: {}", taskName, section, e.getMessage());
            return TaskSchemaValidationResult.invalid(List.of("Schema validation error: " + e.getMessage()));
        }
    }
}
