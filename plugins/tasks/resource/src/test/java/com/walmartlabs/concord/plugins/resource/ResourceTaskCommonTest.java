package com.walmartlabs.concord.plugins.resource;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTaskCommonTest {

    @Test
    void testPrettyPrintYaml() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("x", 123);
        m.put("y", Collections.singletonMap("a", false));
        m.put("jsr310", OffsetDateTime.now());

        assertValidYaml(ResourceTaskCommon.prettyPrintYaml(m, 0));
        assertValidYaml(String.format("value: %s", ResourceTaskCommon.prettyPrintYaml(m, 2)));
        assertValidYaml(String.format("value: %s", ResourceTaskCommon.prettyPrintYaml(Arrays.asList("a", "b", "c"), 2)));
    }

    @Test
    void testPrintJson() throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("x", 123);
        m.put("y", "hello");

        String result = ResourceTaskCommon.printJson(m);

        assertFalse(result.contains("\n"));
        assertTrue(result.contains("\"x\":123"));
        assertTrue(result.contains("\"y\":\"hello"));
    }

    @Test
    void testPrettyPrintJson() throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("x", 123);
        m.put("y", "hello");

        String result = ResourceTaskCommon.prettyPrintJson(m);

        assertTrue(result.contains("\n"));
        assertTrue(result.contains("\"x\" : 123"));
        assertTrue(result.contains("\"y\" : \"hello\""));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void testFromJsonString() throws Exception {
        String jsonString = "{ \"stringKey\": \"stringValue\", \"listKey\": [1,2,3]}";
        Path workDir = Paths.get(System.getProperty("user.dir"));

        ResourceTaskCommon rsc = new ResourceTaskCommon(workDir,
                (prefix, suffix) -> createTempFile(workDir, prefix, suffix), null);
        Object obj = rsc.fromJsonString(jsonString);

        assertTrue(obj instanceof Map);
        assertEquals("stringValue", ((Map) obj).get("stringKey"));
        List l = (List) ((Map) obj).get("listKey");
        assertEquals(3, l.size());
        assertEquals(2, l.get(1));
    }

    @Test
    void testAsProperties() throws Exception {
        Path workDir = Paths.get(System.getProperty("user.dir"));

        ResourceTaskCommon rsc = new ResourceTaskCommon(workDir,
                (prefix, suffix) -> createTempFile(workDir, prefix, suffix), null);

        Map<String, Object> result = rsc.asProperties(resource("test.properties").toString());

        assertEquals("value2", result.get("param2"));
    }

    private static void assertValidYaml(String s) throws IOException {
        new ObjectMapper(new YAMLFactory()).readValue(s, Object.class);
    }

    private static Path createTempFile(Path baseDir, String prefix, String suffix) throws IOException {
        Path tempDir = assertTempDir(baseDir);
        return Files.createTempFile(tempDir, prefix, suffix);
    }

    private static Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        if (!p.toFile().exists()) {
            Files.createDirectories(p);
        }

        return p;
    }

    private static Path resource(String name) throws URISyntaxException {
        URL url = ResourceTaskCommonTest.class.getResource(name);
        assertNotNull(url, "can't find '" + name + "'");
        return Paths.get(url.toURI());
    }
}
