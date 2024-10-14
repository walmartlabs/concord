package com.walmartlabs.concord.plugins.input.parser;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParamDefinitionParserTest {

    @Test
    public void testParse_withSingleSimpleParam() {
        String input = "stringParam: STRING, optional, This is a string parameter";
        ParamDefinition expected = ParamDefinition.builder()
                .optional(true)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        Map<String, ParamDefinition> result = ParamDefinitionParser.parse(toMap(input));

        assertEquals(Collections.singletonMap("stringParam", expected), result);
    }

    @Test
    public void testParse_withTwoSimpleParam() {
        String input =
                "stringParam: STRING, optional, This is a string parameter\n" +
                "booleanParam: boolean, mandatory, This is boolean parameter";

        ParamDefinition expectedString = ParamDefinition.builder()
                .optional(true)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        ParamDefinition expectedBoolean = ParamDefinition.builder()
                .optional(false)
                .description("This is boolean parameter")
                .type(ParamType.BOOLEAN)
                .build();

        Map<String, ParamDefinition> result = ParamDefinitionParser.parse(toMap(input));

        Map<String, Object> expected = new HashMap<>();
        expected.put("stringParam", expectedString);
        expected.put("booleanParam", expectedBoolean);

        assertEquals(expected, result);
    }

    @Test
    public void testParse_withComplexParam() {
        String input =
                "objectParam: object, optional, This is an object parameter\n" +
                "objectParam.stringParam: STRING, optional, This is a string parameter";
        ParamDefinition expected = ParamDefinition.builder()
                .optional(true)
                .description("This is an object parameter")
                .type(ParamType.OBJECT)
                .build();

        ParamDefinition expectedValue = ParamDefinition.builder()
                .optional(true)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        Map<String, ParamDefinition> result = ParamDefinitionParser.parse(toMap(input));

        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("objectParam", expected);
        expectedResult.put("objectParam.stringParam", expectedValue);

        assertEquals(expectedResult, result);
    }

    @Test
    public void testParse_withArrayType() {
        String input =
                "arrayParam: array, mandatory, This is array parameter\n" +
                "arrayParam.stringParam: STRING, optional, This is a string parameter";

        ParamDefinition expected = ParamDefinition.builder()
                .optional(false)
                .description("This is array parameter")
                .type(ParamType.ARRAY)
                .build();
        ParamDefinition expectedValue = ParamDefinition.builder()
                .optional(true)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        Map<String, ParamDefinition> result = ParamDefinitionParser.parse(toMap(input));

        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("arrayParam", expected);
        expectedResult.put("arrayParam.stringParam", expectedValue);

        assertEquals(expectedResult, result);
    }

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<Map<String, String>>() {
    };

    private Map<String, String> toMap(String input) {
        try {
            return new ObjectMapper(new YAMLFactory()).readValue(input, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
