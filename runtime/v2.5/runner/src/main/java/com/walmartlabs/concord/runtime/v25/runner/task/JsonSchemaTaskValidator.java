package com.walmartlabs.concord.runtime.v25.runner.task;

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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class JsonSchemaTaskValidator implements TaskRuntime.Validator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaTaskValidator.class);

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final ConcurrentMap<Key, CompiledSchema> schemas = new ConcurrentHashMap<>();

    @Override
    public void validateInput(String taskName, Map<String, Object> input) {
    }

    @Override
    public void validateOutput(String taskName, Map<String, Object> output) {
    }

    @Override
    public void validateInput(String taskName, Class<? extends Task> taskClass, Map<String, Object> input,
                              TaskRuntime.ValidationMode mode) {
        validate(taskName, taskClass, "in", input, mode);
    }

    @Override
    public void validateOutput(String taskName, Class<? extends Task> taskClass, Map<String, Object> output,
                               TaskRuntime.ValidationMode mode) {
        validate(taskName, taskClass, "out", output, mode);
    }

    private void validate(String taskName, Class<? extends Task> taskClass, String section,
                          Map<String, Object> values, TaskRuntime.ValidationMode mode) {
        if (mode == TaskRuntime.ValidationMode.DISABLED) {
            return;
        }
        var compiled = schemas.computeIfAbsent(new Key(taskName, taskClass, section), this::compile);
        var errors = new ArrayList<>(compiled.errors());
        if (compiled.schema() != null) {
            try {
                compiled.schema().validate(objectMapper.valueToTree(values)).stream()
                        .map(message -> message.getMessage())
                        .sorted()
                        .forEach(errors::add);
            } catch (RuntimeException e) {
                errors.add("Failed to validate task values: " + safeMessage(e));
            }
        }
        if (errors.isEmpty()) {
            return;
        }
        var message = message(taskName, section, compiled.resourceName(), errors);
        if (mode == TaskRuntime.ValidationMode.WARN) {
            log.warn("{}", message);
        } else {
            throw new UserDefinedException(message, Map.of(
                    "taskName", taskName,
                    "section", section,
                    "schemaResource", compiled.resourceName(),
                    "errors", List.copyOf(errors)));
        }
    }

    private CompiledSchema compile(Key key) {
        var resourceName = key.taskName() + ".schema.json";
        try (var input = key.taskClass().getResourceAsStream(resourceName)) {
            if (input == null) {
                return new CompiledSchema(null, resourceName, List.of());
            }
            var root = objectMapper.readTree(input);
            if (!root.isObject()) {
                return invalid(resourceName, "Task schema must be a JSON object");
            }
            var section = root.get(key.section());
            if (section == null || section.isNull()) {
                return new CompiledSchema(null, resourceName, List.of());
            }
            if (!section.isObject()) {
                return invalid(resourceName, "Task schema section '" + key.section() + "' must be a JSON object");
            }
            var schema = objectMapper.createObjectNode();
            copy(root, schema, "$schema");
            copy(root, schema, "$id");
            copy(root, schema, "definitions");
            copy(root, schema, "$defs");
            section.fields().forEachRemaining(entry -> schema.set(entry.getKey(), entry.getValue()));
            return new CompiledSchema(schemaFactory.getSchema(schema), resourceName, List.of());
        } catch (IOException | RuntimeException e) {
            return invalid(resourceName, "Failed to load task schema: " + safeMessage(e));
        }
    }

    private static CompiledSchema invalid(String resourceName, String error) {
        return new CompiledSchema(null, resourceName, List.of(error));
    }

    private static void copy(JsonNode source, ObjectNode target, String field) {
        var value = source.get(field);
        if (value != null && !value.isNull()) {
            target.set(field, value);
        }
    }

    private static String message(String taskName, String section, String resourceName, List<String> errors) {
        var result = new StringBuilder("Task '").append(taskName).append("' ").append(section)
                .append(" validation errors (").append(resourceName).append("):");
        errors.forEach(error -> result.append("\n  - ").append(error));
        return result.toString();
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private record Key(String taskName, Class<? extends Task> taskClass, String section) {
    }

    private record CompiledSchema(JsonSchema schema, String resourceName, List<String> errors) {
        private CompiledSchema {
            errors = List.copyOf(errors);
        }
    }
}
