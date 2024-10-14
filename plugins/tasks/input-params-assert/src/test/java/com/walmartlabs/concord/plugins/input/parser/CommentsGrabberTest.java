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

import com.walmartlabs.concord.plugins.input.FlowCallSchemaGeneratorTest;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CommentsGrabberTest {

    @Test
    public void test1() throws Exception {
        Map<String, List<String>> result = new CommentsGrabber().grab(readResource("grabber/001.concord.yaml"));
        assertTrue(result.isEmpty());
    }

    @Test
    public void test2() throws Exception {
        Map<String, List<String>> result = new CommentsGrabber().grab(readResource("grabber/002.concord.yaml"));

        assertEquals(1, result.size());
        assertNotNull(result.get("test"));
        assertEquals(4, result.get("test").size());
    }

    @Test
    public void test3() throws Exception {
        Map<String, List<String>> result = new CommentsGrabber().grab(readResource("grabber/003.concord.yaml"));

        assertEquals(1, result.size());
        assertNotNull(result.get("test"));
        assertEquals(5, result.get("test").size());
    }

    private static Path readResource(String name) throws Exception {
        URL url = FlowCallSchemaGeneratorTest.class.getResource(name);
        if (url == null) {
            throw new RuntimeException("Resource '" + name + "' not found");
        }

        return Paths.get(url.toURI());
    }
}
