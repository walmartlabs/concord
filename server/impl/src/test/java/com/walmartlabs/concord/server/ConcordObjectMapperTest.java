package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcordObjectMapperTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private static final ConcordObjectMapper concordObjectMapper = new ConcordObjectMapper(objectMapper);

    @Test
    void testNul() {
        // Postgres doesn't want a NUL character in JSONB, even if it's escaped.
        var jsonB = concordObjectMapper.toJSONB(Map.of("aNul", ">\u0000<"));

        assertEquals("{\"aNul\":\"><\"}", jsonB.toString());
    }

    @Test
    void testSoh() {
        // Other control characters are fine when escaped, e.g., SOH (Start of Heading, \u0001)
        var jsonB = concordObjectMapper.toJSONB(Map.of("aSoh", ">\u0001<"));

        assertEquals("{\"aSoh\":\">\\u0001<\"}", jsonB.toString());
    }

    @Test
    void testSimilarButNotControl() {
        // Make sure we don't mess with similar but not control characters, e.g., \u0000 preceded by a backslash
        var jsonB = concordObjectMapper.toJSONB(Map.of("notNul", ">\\u0000<"));

        assertEquals("{\"notNul\":\">\\\\u0000<\"}", jsonB.toString());
    }

}
