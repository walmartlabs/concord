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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.runtime.v2.runner.tasks.other.OtherSchemaTask;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaLookupResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class TaskSchemaRegistryTest {

    private ObjectMapper objectMapper;
    private TaskSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        registry = new TaskSchemaRegistry(objectMapper);
    }

    @Test
    void testResourceExistsWithInputAndOutputSections() throws Exception {
        TaskSchemaLookupResult in = registry.getInputSchema("full", RegistryTask.class);
        assertEquals(FOUND, in.status());
        assertEquals("full.schema.json", in.resourceName());
        assertTrue(registry.getRawSchema("full", RegistryTask.class).isPresent());
        assertTrue(in.schema().validate(objectMapper.readTree("""
                { "message": "hello" }
                """)).isEmpty());
        assertFalse(in.schema().validate(objectMapper.readTree("""
                { "message": 123 }
                """)).isEmpty());

        TaskSchemaLookupResult out = registry.getOutputSchema("full", RegistryTask.class);
        assertEquals(FOUND, out.status());
        assertTrue(out.schema().validate(objectMapper.readTree("""
                { "count": 5 }
                """)).isEmpty());
        assertFalse(out.schema().validate(objectMapper.readTree("""
                { "count": "bad" }
                """)).isEmpty());
    }

    @Test
    void testResourceMissing() {
        TaskSchemaLookupResult result = registry.getInputSchema("missing", RegistryTask.class);
        assertEquals(ABSENT, result.status());
        assertFalse(result.hasErrors());
        assertTrue(registry.getRawSchema("missing", RegistryTask.class).isEmpty());
    }

    @Test
    void testRequestedSectionMissing() {
        TaskSchemaLookupResult in = registry.getInputSchema("noSection", RegistryTask.class);
        assertEquals(FOUND, in.status());

        TaskSchemaLookupResult out = registry.getOutputSchema("noSection", RegistryTask.class);
        assertEquals(NO_SECTION, out.status());
        assertFalse(out.hasErrors());
        assertEquals("noSection.schema.json", out.resourceName());
    }

    @Test
    void testMalformedJsonResource() {
        TaskSchemaLookupResult result = registry.getInputSchema("invalidJson", RegistryTask.class);
        assertEquals(INVALID, result.status());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().get(0).contains("Failed to load schema resource"));
        assertEquals("invalidJson.schema.json", result.resourceName());
    }

    @Test
    void testNonObjectRoot() {
        TaskSchemaLookupResult result = registry.getInputSchema("rootNotObject", RegistryTask.class);
        assertEquals(INVALID, result.status());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().get(0).contains("must be a JSON object"));
    }

    @Test
    void testNonObjectSection() {
        TaskSchemaLookupResult in = registry.getInputSchema("invalidSection", RegistryTask.class);
        assertEquals(INVALID, in.status());
        assertTrue(in.hasErrors());
        assertTrue(in.errors().get(0).contains("section 'in'"));

        TaskSchemaLookupResult out = registry.getOutputSchema("invalidSection", RegistryTask.class);
        assertEquals(FOUND, out.status());
    }

    @Test
    void testCacheKeyIncludesTaskClass() throws Exception {
        TaskSchemaLookupResult local = registry.getInputSchema("shared", RegistryTask.class);
        TaskSchemaLookupResult other = registry.getInputSchema("shared", OtherSchemaTask.class);

        assertEquals(FOUND, local.status());
        assertEquals(FOUND, other.status());
        assertTrue(local.schema().validate(objectMapper.readTree("""
                { "local": "a" }
                """)).isEmpty());
        assertFalse(local.schema().validate(objectMapper.readTree("""
                { "other": "b" }
                """)).isEmpty());
        assertTrue(other.schema().validate(objectMapper.readTree("""
                { "other": "b" }
                """)).isEmpty());
        assertFalse(other.schema().validate(objectMapper.readTree("""
                { "local": "a" }
                """)).isEmpty());
    }

    public static class RegistryTask implements Task {

        @Override
        public TaskResult execute(Variables input) {
            return TaskResult.success();
        }
    }
}
