package com.walmartlabs.concord.server.template;

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
import com.walmartlabs.concord.common.ConfigurationUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConfigurationUtilsTest {

    @Test
    @SuppressWarnings("unchecked")
    public void test() throws Exception {
        String json = "{ \"smtp\": { \"host\": \"localhost\" }, \"ssl\": false }";

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> m = om.readValue(json, Map.class);

        assertEquals("localhost", ConfigurationUtils.get(m, "smtp", "host"));
        assertEquals(false, ConfigurationUtils.get(m, "ssl"));

        // ---

        Map<String, Object> n = new HashMap<>();
        n.put("host", "127.0.0.1");

        ConfigurationUtils.merge(m, n, "smtp");

        assertEquals("127.0.0.1", ConfigurationUtils.get(m, "smtp", "host"));

        // ---

        String newJson = "{ \"smtp\": { \"host\": \"10.11.12.13\" } }";
        Map<String, Object> nn = om.readValue(newJson, Map.class);

        ConfigurationUtils.merge(m, nn);

        assertEquals("10.11.12.13", ConfigurationUtils.get(m, "smtp", "host"));
        assertEquals(false, ConfigurationUtils.get(m, "ssl"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeepMerge() throws Exception {
        ObjectMapper om = new ObjectMapper();

        String cfgJson = "{ \"arguments\": { \"smtpParams\": { \"host\": \"localhost\" } } }";
        Map<String, Object> cfg = om.readValue(cfgJson, Map.class);

        String requestJson = "{ \"arguments\": { \"mail\": \"hello\" } }";
        Map<String, Object> request = om.readValue(requestJson, Map.class);

        // ---

        Map<String, Object> m = ConfigurationUtils.deepMerge(cfg, request);
        assertEquals("localhost", ConfigurationUtils.get(m, "arguments", "smtpParams", "host"));
        assertEquals("hello", ConfigurationUtils.get(m, "arguments", "mail"));
    }

    @Test
    public void testDeepMergeEmptyLeftSide() {
        Map<String, Object> a = Collections.emptyMap();

        Map<String, Object> b = new HashMap<>();
        b.put("test", 123);

        a = ConfigurationUtils.deepMerge(a, b);
        assertEquals(123, a.get("test"));
    }

    @Test
    public void testMerge() {
        Map<String, Object> a = new HashMap<>();
        a.put("x", 123);

        Map<String, Object> b = new HashMap<>();
        b.put("y", 234);

        ConfigurationUtils.merge(a, b);
        assertEquals(234, a.get("y"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToNested() {
        String k = "a.b.c";
        Object v = 123;

        Map<String, Object> m = ConfigurationUtils.toNested(k, v);
        assertEquals(1, m.size());

        Map<String, Object> m1 = (Map<String, Object>) m.get("a");
        assertNotNull(m1);

        Map<String, Object> m2 = (Map<String, Object>) m1.get("b");
        assertNotNull(m2);

        Object vv = m2.get("c");
        assertEquals(v, vv);
    }

    @Test
    public void testToNestedMinimal() {
        String k = "a";
        Object v = 123;

        Map<String, Object> m = ConfigurationUtils.toNested(k, v);
        assertEquals(1, m.size());

        Object vv = m.get("a");
        assertEquals(v, vv);
    }
}
