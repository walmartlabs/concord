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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TriggerInternalIdCalculatorTest {

    @Test
    public void testEmpty() {
        String name = "trigger";
        List<String> activeProfiles = List.of();
        Map<String, Object> arguments = Map.of();
        Map<String, Object> conditions = Map.of();
        Map<String, Object> cfg = Map.of();

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg);
        assertEquals(id1, id2);
    }

    @Test
    public void test1() {
        String name = "trigger";
        List<String> activeProfiles = List.of();
        Map<String, Object> arguments = Map.of();
        Map<String, Object> conditions = Map.of();

        Map<String, Object> cfg1 = Map.of(
                "a", "a-value",
                "b", Collections.singletonMap("k", "v"),
                "c", "c-value");

        Map<String, Object> cfg2 = new LinkedHashMap<>();
        cfg2.put("c", "c-value");
        cfg2.put("a", "a-value");
        cfg2.put("b", Collections.singletonMap("k", "v"));

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg1);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg2);
        assertEquals(id1, id2);
    }

    @Test
    public void testArrayOfObjects() {
        String name = "trigger";
        List<String> activeProfiles = List.of();

        Map<String, Object> arguments = Map.of(
                "listOfMaps", List.of(Map.of("name", "one"), Map.of("name", "two")),
                "anotherProblem", Arrays.asList("a", 2, false));

        Map<String, Object> conditions = Map.of();

        Map<String, Object> configuration = Map.of(
                "name", "MyTrigger",
                "entryPoint", "default");

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, configuration);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, configuration);
        assertEquals(id1, id2);
    }

    @Test
    public void testNotEquals() {
        String name = "trigger";
        List<String> activeProfiles = List.of();
        Map<String, Object> arguments = Map.of();
        Map<String, Object> conditions = Map.of();

        Map<String, Object> cfg1 = Map.of(
                "a", "a-value",
                "b", Map.of("k", "v"),
                "c", "c-value");

        Map<String, Object> cfg2 = Map.of(
                "c", "c-value",
                "a", "a-value",
                "boom", Map.of("k", "v"));

        String id1 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg1);
        String id2 = TriggerInternalIdCalculator.getId(name, activeProfiles, arguments, conditions, cfg2);
        assertNotEquals(id1, id2);
    }
}
