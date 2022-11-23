package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationUtilsTest {

    @Test
    public void deepMergeTest() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put("a", "a-value1");
        m1.put("b", "b-value1");

        Map<String, Object> m2 = new HashMap<>();
        m2.put("a", "a-value2");
        m2.put("c", "b-value2");

        Map<String, Object> result = ConfigurationUtils.deepMerge(m1, m2);
        assertEquals("a-value2", result.get("a"));
        assertEquals("b-value1", result.get("b"));
        assertEquals("b-value2", result.get("c"));
    }

    @Test
    public void deepEqualsTest() {
        Object a = Collections.singletonMap("x", Collections.singletonList("test1"));
        Object b = Collections.singletonMap("x", Collections.singletonList("test2"));
        assertFalse(ConfigurationUtils.deepEquals(a, b));

        a = Collections.singletonMap("x", Collections.singletonList("test"));
        b = Collections.singletonMap("x", Collections.singletonList("test"));
        assertTrue(ConfigurationUtils.deepEquals(a, b));
    }
}
