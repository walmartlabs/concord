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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.input.parser.CommentParser;
import com.walmartlabs.concord.plugins.input.parser.CommentsGrabber;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlowCallSchemaGeneratorTest {

    private final FlowCallSchemaGenerator generator = new FlowCallSchemaGenerator(new CommentsGrabber(), new CommentParser());

    @Test
    public void tests() throws Exception {
        Map<String, Object> schema = generator.generate(readResource("ok/001-concord.yaml"));
        assertSchema("ok/001-json-schema.json", schema);

        schema = generator.generate(readResource("ok/002-concord.yaml"));
        assertSchema("ok/002-json-schema.json", schema);

        schema = generator.generate(readResource("ok/003-concord.yaml"));
        assertSchema("ok/003-json-schema.json", schema);

        schema = generator.generate(readResource("ok/004-concord.yaml"));
        assertSchema("ok/004-json-schema.json", schema);

        schema = generator.generate(readResource("ok/005-concord.yaml"));
        assertSchema("ok/005-json-schema.json", schema);
    }

    private static void assertSchema(String fileName, Map<String, Object> schema) {
        assertEquals(getResourceFileAsString(fileName), writeAsString(schema));
    }

    static String getResourceFileAsString(String fileName) {
        try (InputStream is = FlowCallSchemaGeneratorTest.class.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new RuntimeException("Resource '" + fileName + "' not found");
            }
            try (InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path readResource(String name) throws Exception {
        URL url = FlowCallSchemaGeneratorTest.class.getResource(name);
        if (url == null) {
            throw new RuntimeException("Resource '" + name + "' not found");
        }

        return Paths.get(url.toURI());
    }

    private static String writeAsString(Object value) {
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Error writing value '" + value + "' to string: " + e.getMessage());
        }
    }
}
