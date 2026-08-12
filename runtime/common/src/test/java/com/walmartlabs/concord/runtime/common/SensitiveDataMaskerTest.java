package com.walmartlabs.concord.runtime.common;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SensitiveDataMaskerTest {

    @Test
    public void testSensitiveDataMasking() throws JsonProcessingException {
        var sensitiveStrings = Set.of("foo", "bar");

        String in = "{" +
                "\"a\": \"foo\"," +
                "\"b\": \"bar\"," +
                "\"c\": \"baz\"," +
                "\"d\": { \"e\": \"foo\" }" +
                "}";

        Map<String, Object> result = SensitiveDataMasker.mask(vars(in), sensitiveStrings);
        String expected = "{" +
                "   \"a\": \"******\"," +
                "   \"b\": \"******\"," +
                "   \"c\": \"baz\"," +
                "   \"d\": { \"e\": \"******\" }" +
                "}";
        assertEquals(vars(expected), result);
    }

    private static Map<String, Object> vars(String in) throws JsonProcessingException {
        return new ObjectMapper().readValue(in, new TypeReference<>() {
        });
    }
}
