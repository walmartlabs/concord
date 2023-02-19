package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class QueueRuleTest {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    @Test
    public void testDeserialize() {
        Map<String, Object> process = new HashMap<>();
        process.put("msg", "Process message");
        process.put("RUNNING", 1);
        process.put("FAILED", 2);

        Map<String, Object> processPerOrg = new HashMap<>();
        processPerOrg.put("msg", "Process per org message");
        processPerOrg.put("RUNNING2", 2);

        Map<String, Object> processPerProject = new HashMap<>();
        processPerProject.put("msg", "Process per project message");
        processPerProject.put("RUNNING3", 3);

        Map<String, Object> concurrent = new HashMap<>();
        concurrent.put("msg", "Concurrent message");
        concurrent.put("maxPerOrg", 4);
        concurrent.put("maxPerProject", 5);

        Map<String, Object> rules = new HashMap<>();
        rules.put("concurrent", concurrent);
        rules.put("process", process);
        rules.put("processPerOrg", processPerOrg);
        rules.put("processPerProject", processPerProject);

        QueueRule r = objectMapper.convertValue(rules, QueueRule.class);

        assertNotNull(r.concurrentRule());
        assertEquals(4, (int) r.concurrentRule().maxPerOrg());
        assertEquals(5, (int) r.concurrentRule().maxPerProject());
        assertEquals("Concurrent message", r.concurrentRule().msg());
    }
}
