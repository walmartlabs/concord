package com.walmartlabs.concord.runtime.v2.sdk;

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
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TaskResultSerializationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testSerialization() {
        TaskResult result = TaskResult.success()
                .value("key", "value")
                .value("values", Collections.singletonList("1"))
                .value("value", "v-e");

        ObjectMapper om = new ObjectMapper();

        Map<String, Object> m = om.convertValue(result, Map.class);
        assertEquals(4, m.size());
        assertEquals("value", m.get("key"));
        assertEquals("v-e", m.get("value"));
        assertEquals(Collections.singletonList("1"), m.get("values"));
    }
}
