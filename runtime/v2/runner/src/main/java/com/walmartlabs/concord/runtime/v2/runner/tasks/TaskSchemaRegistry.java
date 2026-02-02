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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.walmartlabs.concord.runtime.common.injector.TaskHolder;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for task JSON schemas.
 * Schemas are loaded from resource files next to task classes.
 * Schema naming convention: {@code <task-name>.schema.json} in the same package as the task class.
 * <p>
 * Only JSON Schema draft-07 is supported.
 */
@Named
@Singleton
public class TaskSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskSchemaRegistry.class);

    /**
     * Cache entry holding both raw schema and compiled in/out schemas.
     */
    private record CachedSchema(
            JsonNode rawSchema,
            JsonSchema inSchema,   // null if no 'in' section
            JsonSchema outSchema   // null if no 'out' section
    ) {}

    private final ConcurrentMap<String, Optional<CachedSchema>> cache = new ConcurrentHashMap<>();
    private final TaskHolder<Task> taskHolder;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    @Inject
    public TaskSchemaRegistry(TaskHolder<Task> taskHolder) {
        this.taskHolder = taskHolder;
        this.objectMapper = new ObjectMapper();
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Get the compiled input schema for a task.
     *
     * @param taskName the task name
     * @return the compiled input schema, or empty if no schema or no 'in' section found
     */
    public Optional<JsonSchema> getInputSchema(String taskName) {
        return cache.computeIfAbsent(taskName, this::loadSchema)
                .filter(cached -> cached.inSchema() != null)
                .map(CachedSchema::inSchema);
    }

    /**
     * Get the compiled output schema for a task.
     *
     * @param taskName the task name
     * @return the compiled output schema, or empty if no schema or no 'out' section found
     */
    public Optional<JsonSchema> getOutputSchema(String taskName) {
        return cache.computeIfAbsent(taskName, this::loadSchema)
                .filter(cached -> cached.outSchema() != null)
                .map(CachedSchema::outSchema);
    }

    /**
     * Get the ObjectMapper used by this registry.
     * Shared with TaskSchemaValidator to avoid duplicate instances.
     *
     * @return the ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private Optional<CachedSchema> loadSchema(String taskName) {
        Class<? extends Task> taskClass = taskHolder.get(taskName);
        if (taskClass == null) {
            log.debug("Task class not found for '{}', no schema available", taskName);
            return Optional.empty();
        }

        String resourceName = taskName + ".schema.json";
        try (InputStream is = taskClass.getResourceAsStream(resourceName)) {
            if (is == null) {
                log.debug("No schema found for task '{}' (resource: {})", taskName, resourceName);
                return Optional.empty();
            }

            JsonNode rawSchema = objectMapper.readTree(is);
            log.debug("Loaded schema for task '{}' from {}", taskName, resourceName);

            JsonSchema inSchema = compileSection(rawSchema, "in", taskName);
            JsonSchema outSchema = compileSection(rawSchema, "out", taskName);

            return Optional.of(new CachedSchema(rawSchema, inSchema, outSchema));
        } catch (IOException e) {
            log.warn("Failed to load schema for task '{}': {}", taskName, e.getMessage());
            return Optional.empty();
        }
    }

    private JsonSchema compileSection(JsonNode rawSchema, String section, String taskName) {
        JsonNode sectionSchema = rawSchema.get(section);
        if (sectionSchema == null || sectionSchema.isNull()) {
            log.debug("No '{}' section in schema for task '{}'", section, taskName);
            return null;
        }

        if (!sectionSchema.isObject()) {
            log.warn("Schema section '{}' for task '{}' is not an object", section, taskName);
            return null;
        }

        try {
            // Build a new schema that includes root definitions for $ref resolution
            ObjectNode newSchema = objectMapper.createObjectNode();

            // Copy definitions from root schema (supports both "definitions" and "$defs")
            JsonNode definitions = rawSchema.get("definitions");
            if (definitions != null && !definitions.isNull()) {
                newSchema.set("definitions", definitions);
            }
            JsonNode defs = rawSchema.get("$defs");
            if (defs != null && !defs.isNull()) {
                newSchema.set("$defs", defs);
            }

            // Copy all properties from the section schema
            sectionSchema.fields().forEachRemaining(entry ->
                newSchema.set(entry.getKey(), entry.getValue()));

            return schemaFactory.getSchema(newSchema);
        } catch (Exception e) {
            log.warn("Failed to compile '{}' schema for task '{}': {}", section, taskName, e.getMessage());
            return null;
        }
    }
}
