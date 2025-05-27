package com.walmartlabs.concord.runtime.common;

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
