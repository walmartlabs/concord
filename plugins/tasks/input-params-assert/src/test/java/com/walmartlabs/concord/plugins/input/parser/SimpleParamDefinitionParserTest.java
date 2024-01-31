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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimpleParamDefinitionParserTest {

    @Test
    public void testParse_withValidInput() {
        String input = "STRING, optional, This is a string parameter";
        ParamDefinition expectedOutput = ParamDefinition.builder()
                .optional(true)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        ParamDefinition result = SimpleParamDefinitionParser.parse(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testParse_withMandatory() {
        String input = "INTEGER, mandatory, This is an integer parameter";
        ParamDefinition expectedOutput = ParamDefinition.builder()
                .optional(false)
                .description("This is an integer parameter")
                .type(ParamType.NUMBER)
                .build();

        ParamDefinition result = SimpleParamDefinitionParser.parse(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testParse_withNoTypeAndOptional() {
        String input = "This is a parameter without type and optional";

        assertThrows(RuntimeException.class, () -> {
            SimpleParamDefinitionParser.parse(input);
        });
    }

    @Test
    public void testParse_withNoOptional() {
        String input = "STRING, This is a string parameter";
        ParamDefinition expectedOutput = ParamDefinition.builder()
                .optional(false)
                .description("This is a string parameter")
                .type(ParamType.STRING)
                .build();

        ParamDefinition result = SimpleParamDefinitionParser.parse(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testParse_withNoOptionalAndNoDescription() {
        String input = "STRING";
        ParamDefinition expectedOutput = ParamDefinition.builder()
                .optional(false)
                .type(ParamType.STRING)
                .build();

        ParamDefinition result = SimpleParamDefinitionParser.parse(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testParse_withUnknownType() {
        String input = "UNKNOWN_TYPE, optional, This is an unknown type parameter";

        assertThrows(RuntimeException.class, () -> {
            SimpleParamDefinitionParser.parse(input);
        });
    }

    @Test
    public void testParse_withArray() {
        String input = "string[], optional, This is a string array parameter";
        ParamDefinition expectedOutput = ParamDefinition.builder()
                .optional(true)
                .description("This is a string array parameter")
                .type(ParamType.STRING)
                .isSimpleArray(true)
                .build();

        ParamDefinition result = SimpleParamDefinitionParser.parse(input);

        assertEquals(expectedOutput, result);
    }

}
