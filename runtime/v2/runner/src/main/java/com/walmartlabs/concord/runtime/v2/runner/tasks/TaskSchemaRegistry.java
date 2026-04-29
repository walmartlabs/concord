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
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for task JSON schemas.
 * Schemas are loaded from resource files next to task classes.
 * Schema authoring convention:
 * <ul>
 *     <li>resource name: {@code <task-name>.schema.json}</li>
 *     <li>resource location: same package as the task class</li>
 *     <li>JSON Schema draft: draft-07</li>
 *     <li>top-level validation sections: {@code in} and {@code out}</li>
 *     <li>shared definitions: {@code definitions} and {@code $defs}</li>
 * </ul>
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
            SectionSchema inSchema,
            SectionSchema outSchema,
            String resourceName
    ) {

        private TaskSchemaLookupResult section(String section) {
            return switch (section) {
                case "in" -> inSchema.toLookupResult(rawSchema, resourceName);
                case "out" -> outSchema.toLookupResult(rawSchema, resourceName);
                default -> throw new IllegalArgumentException("Unknown schema section: " + section);
            };
        }

        private static CachedSchema absent(String resourceName) {
            SectionSchema absent = SectionSchema.absent();
            return new CachedSchema(null, absent, absent, resourceName);
        }

        private static CachedSchema invalid(String resourceName, List<String> errors) {
            SectionSchema invalid = SectionSchema.invalid(errors);
            return new CachedSchema(null, invalid, invalid, resourceName);
        }
    }

    private record SectionSchema(
            TaskSchemaLookupResult.Status status,
            JsonSchema schema,
            List<String> errors
    ) {

        private SectionSchema {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }

        private TaskSchemaLookupResult toLookupResult(JsonNode rawSchema, String resourceName) {
            return switch (status) {
                case ABSENT -> TaskSchemaLookupResult.absent(resourceName);
                case NO_SECTION -> TaskSchemaLookupResult.noSection(rawSchema, resourceName);
                case INVALID -> TaskSchemaLookupResult.invalid(rawSchema, resourceName, errors);
                case FOUND -> TaskSchemaLookupResult.found(schema, rawSchema, resourceName);
            };
        }

        private static SectionSchema absent() {
            return new SectionSchema(TaskSchemaLookupResult.Status.ABSENT, null, List.of());
        }

        private static SectionSchema noSection() {
            return new SectionSchema(TaskSchemaLookupResult.Status.NO_SECTION, null, List.of());
        }

        private static SectionSchema invalid(List<String> errors) {
            return new SectionSchema(TaskSchemaLookupResult.Status.INVALID, null, errors);
        }

        private static SectionSchema found(JsonSchema schema) {
            return new SectionSchema(TaskSchemaLookupResult.Status.FOUND, schema, List.of());
        }
    }

    private record SchemaKey(String taskName, Class<? extends Task> taskClass) {}

    private final ConcurrentMap<SchemaKey, CachedSchema> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    @Inject
    public TaskSchemaRegistry(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Get the compiled input schema for a task.
     *
     * @param taskName  the task name
     * @param taskClass the resolved task class
     * @return the input schema lookup result
     */
    public TaskSchemaLookupResult getInputSchema(String taskName, Class<? extends Task> taskClass) {
        return getSection(taskName, taskClass, "in");
    }

    /**
     * Get the compiled output schema for a task.
     *
     * @param taskName  the task name
     * @param taskClass the resolved task class
     * @return the output schema lookup result
     */
    public TaskSchemaLookupResult getOutputSchema(String taskName, Class<? extends Task> taskClass) {
        return getSection(taskName, taskClass, "out");
    }

    /**
     * Get the raw task schema resource for future docs/UI use.
     *
     * @param taskName  the task name
     * @param taskClass the resolved task class
     * @return raw task schema resource, if present and parseable
     */
    public Optional<JsonNode> getRawSchema(String taskName, Class<? extends Task> taskClass) {
        if (taskClass == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(cache.computeIfAbsent(new SchemaKey(taskName, taskClass), this::loadSchema)
                .rawSchema());
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

    private TaskSchemaLookupResult getSection(String taskName, Class<? extends Task> taskClass, String section) {
        if (taskClass == null) {
            log.debug("Task class not found for '{}', no schema available", taskName);
            return TaskSchemaLookupResult.absent(resourceName(taskName));
        }

        return cache.computeIfAbsent(new SchemaKey(taskName, taskClass), this::loadSchema)
                .section(section);
    }

    private CachedSchema loadSchema(SchemaKey key) {
        String taskName = key.taskName();
        Class<? extends Task> taskClass = key.taskClass();
        String resourceName = taskName + ".schema.json";
        try (InputStream is = taskClass.getResourceAsStream(resourceName)) {
            if (is == null) {
                log.debug("No schema found for task '{}' (resource: {})", taskName, resourceName);
                return CachedSchema.absent(resourceName);
            }

            JsonNode rawSchema = objectMapper.readTree(is);
            if (!rawSchema.isObject()) {
                String msg = "Schema resource '" + resourceName + "' for task '" + taskName + "' must be a JSON object";
                log.warn(msg);
                return CachedSchema.invalid(resourceName, List.of(msg));
            }

            log.debug("Loaded schema for task '{}' from {}", taskName, resourceName);

            SectionSchema inSchema = compileSection(rawSchema, "in", taskName, resourceName);
            SectionSchema outSchema = compileSection(rawSchema, "out", taskName, resourceName);

            return new CachedSchema(rawSchema, inSchema, outSchema, resourceName);
        } catch (IOException e) {
            String msg = "Failed to load schema resource '" + resourceName + "' for task '" + taskName + "': " + e.getMessage();
            log.warn(msg);
            return CachedSchema.invalid(resourceName, List.of(msg));
        }
    }

    private SectionSchema compileSection(JsonNode rawSchema, String section, String taskName, String resourceName) {
        JsonNode sectionSchema = rawSchema.get(section);
        if (sectionSchema == null || sectionSchema.isNull()) {
            log.debug("No '{}' section in schema for task '{}'", section, taskName);
            return SectionSchema.noSection();
        }

        if (!sectionSchema.isObject()) {
            String msg = "Schema resource '" + resourceName + "' section '" + section + "' for task '" + taskName + "' must be a JSON object";
            log.warn(msg);
            return SectionSchema.invalid(List.of(msg));
        }

        try {
            // Build a new schema that includes root definitions for $ref resolution
            ObjectNode newSchema = objectMapper.createObjectNode();

            copyIfPresent(rawSchema, newSchema, "$schema");
            copyIfPresent(rawSchema, newSchema, "$id");
            copyIfPresent(rawSchema, newSchema, "definitions");
            copyIfPresent(rawSchema, newSchema, "$defs");

            sectionSchema.fields().forEachRemaining(entry ->
                newSchema.set(entry.getKey(), entry.getValue()));

            return SectionSchema.found(schemaFactory.getSchema(newSchema));
        } catch (Exception e) {
            String msg = "Failed to compile schema resource '" + resourceName + "' section '" + section + "' for task '" + taskName + "': " + e.getMessage();
            log.warn(msg);
            return SectionSchema.invalid(List.of(msg));
        }
    }

    private void copyIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode value = from.get(field);
        if (value != null && !value.isNull()) {
            to.set(field, value);
        }
    }

    private static String resourceName(String taskName) {
        return taskName + ".schema.json";
    }
}
