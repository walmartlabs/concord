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
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TaskSchemaValidatorTest {

    private TaskSchemaRegistry registry;
    private TaskSchemaValidator validator;
    private ObjectMapper objectMapper;
    private JsonSchemaFactory schemaFactory;

    @BeforeEach
    void setUp() {
        registry = mock(TaskSchemaRegistry.class);
        objectMapper = new ObjectMapper();
        when(registry.getObjectMapper()).thenReturn(objectMapper);
        validator = new TaskSchemaValidator(registry);
        schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    @Test
    void testNoSchema() {
        when(registry.getInputSchema("testTask")).thenReturn(Optional.empty());

        TaskSchemaValidationResult result = validator.validateInput("testTask", Map.of("key", "value"));

        assertEquals(TaskSchemaValidationResult.Status.NO_SCHEMA, result.status());
        assertFalse(result.hasErrors());
    }

    @Test
    void testValidInput() throws Exception {
        JsonNode schemaNode = objectMapper.readTree("""
            {
                "type": "object",
                "properties": {
                    "message": { "type": "string" }
                },
                "required": ["message"]
            }
            """);

        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        when(registry.getInputSchema("testTask")).thenReturn(Optional.of(schema));

        TaskSchemaValidationResult result = validator.validateInput("testTask", Map.of("message", "hello"));

        assertEquals(TaskSchemaValidationResult.Status.VALID, result.status());
        assertFalse(result.hasErrors());
    }

    @Test
    void testInvalidInput() throws Exception {
        JsonNode schemaNode = objectMapper.readTree("""
            {
                "type": "object",
                "properties": {
                    "message": { "type": "string" }
                },
                "required": ["message"]
            }
            """);

        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        when(registry.getInputSchema("testTask")).thenReturn(Optional.of(schema));

        // Missing required 'message'
        TaskSchemaValidationResult result = validator.validateInput("testTask", Map.of("other", "value"));

        assertEquals(TaskSchemaValidationResult.Status.INVALID, result.status());
        assertTrue(result.hasErrors());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void testMultipleErrors() throws Exception {
        JsonNode schemaNode = objectMapper.readTree("""
            {
                "type": "object",
                "properties": {
                    "message": { "type": "string" },
                    "count": { "type": "integer", "minimum": 0 }
                },
                "required": ["message", "count"]
            }
            """);

        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        when(registry.getInputSchema("testTask")).thenReturn(Optional.of(schema));

        // Missing 'message' and 'count'
        TaskSchemaValidationResult result = validator.validateInput("testTask", Map.of());

        assertEquals(TaskSchemaValidationResult.Status.INVALID, result.status());
        assertTrue(result.hasErrors());
        // Should have at least 2 errors (missing message and count)
        assertTrue(result.errors().size() >= 2);
    }

    @Test
    void testValidOutput() throws Exception {
        JsonNode schemaNode = objectMapper.readTree("""
            {
                "type": "object",
                "properties": {
                    "ok": { "type": "boolean" },
                    "result": { "type": "string" }
                },
                "required": ["ok"]
            }
            """);

        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        when(registry.getOutputSchema("testTask")).thenReturn(Optional.of(schema));

        TaskSchemaValidationResult result = validator.validateOutput("testTask",
                Map.of("ok", true, "result", "success"));

        assertEquals(TaskSchemaValidationResult.Status.VALID, result.status());
        assertFalse(result.hasErrors());
    }

    @Test
    void testTypeValidation() throws Exception {
        JsonNode schemaNode = objectMapper.readTree("""
            {
                "type": "object",
                "properties": {
                    "count": { "type": "integer" }
                }
            }
            """);

        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        when(registry.getInputSchema("testTask")).thenReturn(Optional.of(schema));

        // String instead of integer
        TaskSchemaValidationResult result = validator.validateInput("testTask", Map.of("count", "not-a-number"));

        assertEquals(TaskSchemaValidationResult.Status.INVALID, result.status());
        assertTrue(result.hasErrors());
    }

    @Test
    void testNoOutputSchema() {
        when(registry.getOutputSchema("testTask")).thenReturn(Optional.empty());

        TaskSchemaValidationResult result = validator.validateOutput("testTask", Map.of("key", "value"));

        assertEquals(TaskSchemaValidationResult.Status.NO_SCHEMA, result.status());
        assertFalse(result.hasErrors());
    }
}
