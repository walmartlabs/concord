package com.walmartlabs.concord.server.org.triggers;

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

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TriggerInternalIdCalculatorTest {

    @Test
    public void testEmpty() {
        String name = "trigger";
        List<String> activeProfiles = new ArrayList<>();
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> conditions = new HashMap<>();
        Map<String, Object> cfg = new HashMap<>();

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg);
        assertEquals(id1, id2);
    }

    @Test
    public void test1() {
        String name = "trigger";
        List<String> activeProfiles = new ArrayList<>();
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> conditions = new HashMap<>();

        Map<String, Object> cfg1 = new HashMap<>();
        cfg1.put("a", "a-value");
        cfg1.put("b", Collections.singletonMap("k", "v"));
        cfg1.put("c", "c-value");
        Map<String, Object> cfg2 = new HashMap<>();
        cfg2.put("c", "c-value");
        cfg2.put("a", "a-value");
        cfg2.put("b", Collections.singletonMap("k", "v"));

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg1);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg2);
        assertEquals(id1, id2);
    }
}
