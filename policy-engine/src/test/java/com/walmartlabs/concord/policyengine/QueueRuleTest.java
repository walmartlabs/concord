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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        concurrent.put("max", 4);

        Map<String, Object> rules = new HashMap<>();
        rules.put("concurrent", concurrent);
        rules.put("process", process);
        rules.put("processPerOrg", processPerOrg);
        rules.put("processPerProject", processPerProject);

        QueueRule r = objectMapper.convertValue(rules, QueueRule.class);

        assertNotNull(r.getConcurrent());
        assertEquals(4, (int)r.getConcurrent().getMax());
        assertEquals("Concurrent message", r.getConcurrent().getMsg());

        assertNotNull(r.getProcess());
        assertNotNull(r.getProcess().getMax());
        assertEquals(2, r.getProcess().getMax().size());
        assertEquals(1, (int)r.getProcess().getMax().get("RUNNING"));
        assertEquals(2, (int)r.getProcess().getMax().get("FAILED"));
        assertEquals("Process message", r.getProcess().getMsg());

        assertNotNull(r.getProcessPerOrg());
        assertNotNull(r.getProcessPerOrg().getMax());
        assertEquals(1, r.getProcessPerOrg().getMax().size());
        assertEquals(2, (int)r.getProcessPerOrg().getMax().get("RUNNING2"));
        assertEquals("Process per org message", r.getProcessPerOrg().getMsg());

        assertNotNull(r.getProcessPerProject());
        assertNotNull(r.getProcessPerProject().getMax());
        assertEquals(1, r.getProcessPerProject().getMax().size());
        assertEquals(3, (int)r.getProcessPerProject().getMax().get("RUNNING3"));
        assertEquals("Process per project message", r.getProcessPerProject().getMsg());
    }
}
