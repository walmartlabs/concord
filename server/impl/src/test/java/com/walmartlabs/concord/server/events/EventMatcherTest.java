package com.walmartlabs.concord.server.events;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventMatcherTest {

    @Test
    public void testAllJsonTypes() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");
        event.put("b", "b-value");
        event.put("c", 123);
        event.put("d", null);
        event.put("e", true);
        event.put("f", Arrays.asList("3", "1", "4", "2"));

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("a", "a-v.*");
        conditions.put("b", "b-value");
        conditions.put("c", 123);
        conditions.put("d", null);
        conditions.put("e", true);
        conditions.put("f", Arrays.asList("1", "2"));

        boolean result = EventMatcher.matches(event, conditions);
        assertTrue(result);
    }

    @Test
    public void testNoConditions() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");
        event.put("b", "b-value");
        event.put("c", 123);
        event.put("d", null);
        event.put("e", true);
        event.put("f", Arrays.asList("3", "1", "4", "2"));

        Map<String, Object> conditions = new HashMap<>();

        boolean result = EventMatcher.matches(event, conditions);
        assertTrue(result);
    }

    @Test
    public void testNotMatched() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", "a-value");
        event.put("b", "b-value");
        event.put("c", 123);
        event.put("d", null);
        event.put("e", true);
        event.put("f", Arrays.asList("3", "1", "4", "2"));

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("b", "XXXX");

        boolean result = EventMatcher.matches(event, conditions);
        assertFalse(result);
    }

    @Test
    public void testTypesMismatch() {
        Map<String, Object> event = new HashMap<>();
        event.put("a", 100);

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("a", "123");

        boolean result = EventMatcher.matches(event, conditions);
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
        event.put("obj", Collections.singletonMap("o1", "o1v1"));

        boolean result = EventMatcher.matches(event, conditions);
        assertTrue(result);
    }

}
