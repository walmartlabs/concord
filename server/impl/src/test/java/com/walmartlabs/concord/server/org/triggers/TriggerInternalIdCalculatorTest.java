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

    /**
     * Expected IDs were produced by the former Guava-based implementation.
     * Trigger IDs are stored, so they must not change between versions.
     */
    @Test
    public void testIdsMatchPreviousImplementation() {
        assertEquals("066ccb49ec41ad4d1f11fcb53892ce2ed9dc7884122905a9f79d8e71df605664",
                TriggerInternalIdCalculator.getId("trigger", List.of(), Map.of(), Map.of(), Map.of()));

        Map<String, Object> cfg = Map.of("a", "a-value", "b", Collections.singletonMap("k", "v"), "c", "c-value");
        assertEquals("0522fabaaa110668c10be6f1ebde62245c6fa406b3164d015ef3f6506de1ec62",
                TriggerInternalIdCalculator.getId("trigger", List.of(), Map.of(), Map.of(), cfg));

        Map<String, Object> arguments = Map.of(
                "listOfMaps", List.of(Map.of("name", "one"), Map.of("name", "two")),
                "anotherProblem", Arrays.asList("a", 2, false));
        Map<String, Object> configuration = Map.of("name", "MyTrigger", "entryPoint", "default");
        assertEquals("5a795d08dcf836918fac4bdbe9c56ba5dc7b20e2ea361f85a0293d2c4f5c795b",
                TriggerInternalIdCalculator.getId("trigger", List.of("b", "a"), arguments, Map.of("repository", "r\u00e9po"), configuration));

        // unpaired surrogates are hashed as raw UTF-16 code units
        assertEquals("20039a00e44d15a4d214671c8e1b707e4e1a7d233bef91754126e538f14bf0e8",
                TriggerInternalIdCalculator.getId("tr\uD83D\uDE00igger\uD800", null, Map.of("k\uDC00", "v"), null, Map.of()));
    }
}
