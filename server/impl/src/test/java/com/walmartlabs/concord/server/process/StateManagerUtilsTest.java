package com.walmartlabs.concord.server.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StateManagerUtilsTest {

    @Test
    void testFilterWithJsonFile() throws Exception {
        String jsonInput = "{\"arguments\": \"value\", \"otherKey\": \"otherValue\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8));

        InputStream result = StateManagerUtils.filter("_main.json", inputStream);
        String text = new String(result.readAllBytes(), StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(text, Map.class);

        assertEquals(1, resultMap.size());
        assertEquals("value", resultMap.get("arguments"));
    }

    @Test
    void testFilterWithYamlFile() throws Exception {
        String yamlInput = "arguments: value\notherKey: otherValue";
        InputStream inputStream = new ByteArrayInputStream(yamlInput.getBytes(StandardCharsets.UTF_8));

        InputStream result = StateManagerUtils.filter("test.yaml", inputStream);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> resultMap = mapper.readValue(result, Map.class);

        // return the same if to filter items not present
        assertEquals(2, resultMap.size());
        assertEquals("otherValue", resultMap.get("otherKey"));
    }

    @Test
    void testFilterWithUnsupportedExtension() throws Exception {
        String input = "key: value";
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        InputStream result = StateManagerUtils.filter("file.txt", inputStream);
        String text = new String(result.readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(input, text);
    }

    // Test case for empty input
    @Test
    void testFilterWithEmptyInput() throws Exception {
        String input = "";
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        InputStream result = StateManagerUtils.filter("_main.json", inputStream);
        String text = new String(result.readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(input, text);
    }

    // Test case for null filtering rules
    @Test
    void testFilterWithNullRules() throws Exception {
        String jsonInput = "{\"key\": \"value\"}";
        InputStream inputStream = new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8));

        InputStream result = StateManagerUtils.filter("unknown.json", inputStream);
        String text = new String(result.readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(jsonInput, text);
    }

    // Test case for null input
    @Test
    void testFilterWithNullInput() throws Exception {
        String jsonInput = "{\"key\": \"value\"}";
        InputStream inputStream = null;
        InputStream result = StateManagerUtils.filter("unknown.json", inputStream);

        assertNull(result);
    }
}
