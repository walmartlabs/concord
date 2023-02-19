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

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MatcherTest {

    @Test
    public void testAllJsonTypes() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");
        event.put("b", "b-value");
        event.put("c", 123);
        event.put("d", null);
        event.put("e", true);
        event.put("f", asList("3", "1", "4", "2"));

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("a", "a-v.*");
        conditions.put("b", "b-value");
        conditions.put("c", 123);
        conditions.put("d", null);
        conditions.put("e", true);
        conditions.put("f", asList("1", "2"));

        boolean result = Matcher.matches(event, conditions);
        assertTrue(result);
    }

    @Test
    public void testNoConditions() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");

        Map<String, Object> conditions = new HashMap<>();

        boolean result = Matcher.matches(event, conditions);
        assertFalse(result);
    }

    @Test
    public void testNotMatched() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");
        event.put("b", "b-value");
        event.put("c", 123);
        event.put("d", null);
        event.put("e", true);
        event.put("f", asList("3", "1", "4", "2"));

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("b", "XXXX");

        boolean result = Matcher.matches(event, conditions);
        assertFalse(result);
    }

    @Test
    public void testTypesMismatch() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", 100);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("a", "123");

        boolean result = Matcher.matches(event, conditions);
        assertFalse(result);
    }

    @Test
    public void testObjectsOfObjects() {
        Map<String, Object> m = new HashMap<>();
        m.put("o1", "o1v1");
        m.put("o2", "o2v2");

        Map<String, Object> event = new HashMap<>();
        event.put("a", 100);
        event.put("obj", m);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("a", 100);
        conditions.put("obj", Collections.singletonMap("o1", "o1v1"));

        boolean result = Matcher.matches(event, conditions);
        assertTrue(result);
    }

    @Test
    public void testPartialMatch() {
        Map<String, Object> event = new HashMap<>();
        event.put("unknownRepo", true);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("unknownRepo", asList(true, false));

        boolean result = Matcher.matches(event, conditions);
        assertTrue(result);
    }

    @Test
    public void testPartialNotMatch() {
        Map<String, Object> event = new HashMap<>();
        event.put("unknownRepo", true);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("unknownRepo", Collections.singletonList(false));

        boolean result = Matcher.matches(event, conditions);
        assertFalse(result);
    }

    @Test
    public void testMatchEmptyCondition() {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("params", emptyMap());

        // --- empty param
        Map<String, Object> event1 = new HashMap<>();
        event1.put("k", "v");
        event1.put("params", emptyMap());

        boolean result = Matcher.matches(event1, conditions);
        assertTrue(result);

        // --- param not present
        Map<String, Object> event2 = new HashMap<>();
        event2.put("k", "v");

        boolean result2 = Matcher.matches(event2, conditions);
        assertFalse(result2);

        // --- param present
        Map<String, Object> event3 = new HashMap<>();
        event3.put("k", "v");
        event3.put("params", Collections.singletonMap("a", "a-value"));

        boolean result3 = Matcher.matches(event3, conditions);
        assertFalse(result3);
    }

    @Test
    public void testMatchNullCondition() {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("params", null);

        // --- empty param
        Map<String, Object> event1 = new HashMap<>();
        event1.put("k", "v");
        event1.put("params", emptyMap());

        boolean result = Matcher.matches(event1, conditions);
        assertFalse(result);

        // --- param not present
        Map<String, Object> event2 = new HashMap<>();
        event2.put("k", "v");

        boolean result2 = Matcher.matches(event2, conditions);
        assertTrue(result2);

        // --- param is null
        Map<String, Object> event3 = new HashMap<>();
        event3.put("k", "v");
        event3.put("params", null);

        boolean result3 = Matcher.matches(event3, conditions);
        assertTrue(result3);

        // --- param present
        Map<String, Object> event4 = new HashMap<>();
        event4.put("k", "v");
        event4.put("params", Collections.singletonMap("a", "a-value"));

        boolean result4 = Matcher.matches(event4, conditions);
        assertFalse(result4);
    }

    @Test
    public void testNulls() {
        // data   ?    condition
        //  null == null
        assertTrue(Matcher.matches(null, null));

        //  null == ".*"
        assertTrue(Matcher.matches(null, ".*"));
        //  null == ""
        assertTrue(Matcher.matches(null, ""));

        //  null != {}
        assertFalse(Matcher.matches(null, emptyMap()));

        //  null != []
        assertFalse(Matcher.matches(null, emptyList()));

        //  {} == {}
        assertTrue(Matcher.matches(emptyMap(), emptyMap()));

        //  [] == []
        assertTrue(Matcher.matches(emptyList(), emptyList()));

        //  null != 1
        assertFalse(Matcher.matches(null, 1));

        //  "" != null
        assertFalse(Matcher.matches("", null));

        //  {} != null
        assertFalse(Matcher.matches(emptyMap(), null));

        //  [] != null
        assertFalse(Matcher.matches(emptyList(), null));
    }

    @Test
    public void testOr() {
        // null == [null, []]
        assertTrue(Matcher.matches(null, asList(null, emptyList())));

        // {} == [null, [], {}]
        assertTrue(Matcher.matches(emptyMap(), asList(null, emptyList(), emptyMap())));

        //! [] == [null, []]
        assertFalse(Matcher.matches(emptyList(), asList(null, emptyList())));
    }

    @Test
    public void testArrayMatch() {
        List<String> data = Arrays.asList("one", "two");

        assertTrue(Matcher.matches(data, "on.*"));
        assertFalse(Matcher.matches(data, "ono"));
    }

    // null == null, "", ".*", [null]
    // []   == []
    // {}   == {}, [{}]
}
