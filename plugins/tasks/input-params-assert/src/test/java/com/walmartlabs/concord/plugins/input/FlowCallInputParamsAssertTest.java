package com.walmartlabs.concord.plugins.input;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlowCallInputParamsAssertTest {

    @Test
    public void testTypeMismatch() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/001-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", false);

        try {
            assertion.process("default", input);
            fail("exception expected");
        } catch (UserDefinedException e) {
            assertThat(e.getMessage()).contains("param1: boolean found, string expected");
        }
    }

    @Test
    public void testMissingMandatory() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/002-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();

        try {
            assertion.process("default", input);
            fail("exception expected");
        } catch (UserDefinedException e) {
            assertThat(e.getMessage()).contains("param1: is missing but it is required");
        }
    }

    @Test
    public void testMissingMandatoryObject() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/003-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", Collections.singletonMap("key1", "value"));

        try {
            assertion.process("default", input);
            fail("exception expected");
        } catch (UserDefinedException e) {
            assertThat(e.getMessage()).contains("param1.key2: is missing but it is required");
        }
    }

    @Test
    public void testArrayTypeMismatch() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/004-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", Collections.singletonList(1));

        try {
            assertion.process("default", input);
            fail("exception expected");
        } catch (UserDefinedException e) {
            assertThat(e.getMessage()).contains("param1[0]: integer found, string expected");
        }
    }

    @Test
    public void testOkStringParam() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/001-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "one");
        input.put("param2", "additional");

        assertion.process("default", input);
    }

    @Test
    public void testOkStringMandatoryParam() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/002-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "one");
        input.put("param2", "additional");

        assertion.process("default", input);
    }

    @Test
    public void testOkObjectMandatoryParam() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/003-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> param1 = new HashMap<>();
        param1.put("key1", "value1");
        param1.put("key2", "value2");

        Map<String, Object> input = new HashMap<>();
        input.put("param1", param1);
        input.put("param2", "additional");

        assertion.process("default", input);
    }

    @Test
    public void testOkStringArray() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/004-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("param1", Collections.singletonList("one"));
        input.put("param2", "additional");

        assertion.process("default", input);
    }

    @Test
    public void testOkComplex() throws Exception {
        FlowCallInputParamsAssert assertion = new FlowCallInputParamsAssert(provider("ok/005-json-schema.json"), ProcessConfiguration.builder().build());

        Map<String, Object> input = fromYaml("ok/005-input.yaml");

        assertion.process("default", input);
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static Map<String, Object> fromYaml(String resource) {
        URL url = FlowCallInputParamsAssertTest.class.getResource(resource);
        if (url == null) {
            throw new RuntimeException("Can't find resource: " + resource);
        }

        try {
            return new ObjectMapper(new YAMLFactory()).readValue(url, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final TypeReference<Map<String, JsonNode>> JSON_NODE_MAP_TYPE = new TypeReference<Map<String, JsonNode>>() {
    };

    private static InputParamsDefinitionProvider provider(String resource) throws Exception {
        URL url = FlowCallInputParamsAssertTest.class.getResource(resource);
        if (url == null) {
            throw new RuntimeException("Can't find resource: " + resource);
        }

        PersistenceService persistenceService = mock(PersistenceService.class);
        when(persistenceService.loadPersistedFile(any(), any())).thenReturn(new ObjectMapper().readValue(url, JSON_NODE_MAP_TYPE));

        return new InputParamsDefinitionProvider(persistenceService);
    }
}
