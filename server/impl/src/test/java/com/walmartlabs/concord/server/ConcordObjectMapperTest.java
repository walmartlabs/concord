package com.walmartlabs.concord.server;

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
