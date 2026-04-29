package com.walmartlabs.concord.plugins.mock.matcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.plugins.mock.MockUtilsTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArgsMatcherTest {

    @Test
    void testMatchTwoEqualMaps() {
        Map<String, Object> map1 = Map.of("key1", 123, "key2", "Hello");
        Map<String, Object> map2 = Map.of("key1", 123L, "key2", "hello");

        assertTrue(ArgsMatcher.match(map1, map2));
    }

    @Test
    void testMatchTwoDifferentMaps() {
        Map<String, Object> map1 = Map.of("key1", 123, "key2", "Hello");
        Map<String, Object> map2 = Map.of("key1", 123L, "key2", "World");

        assertFalse(ArgsMatcher.match(map1, map2));
    }

    @Test
    void testMatchTwoEqualLists() {
        List<Object> list1 = List.of(1, 2, "Test");
        List<Object> list2 = List.of(1L, 2, "test");

        assertTrue(ArgsMatcher.match(list1, list2));
    }

    @Test
    void testMatchTwoDifferentLists() {
        List<Object> list1 = List.of(1, 2, "Test");
        List<Object> list2 = List.of(1L, 2, "Fail");

        assertFalse(ArgsMatcher.match(list1, list2));
    }

    @Test
    void testMatchDifferentSizeLists() {
        List<Object> list1 = List.of(1, 2);
        List<Object> list2 = List.of(1L, 2, "extra");

        assertFalse(ArgsMatcher.match(list1, list2));
    }

    @Test
    void testMatchEqualValues() {
        assertTrue(ArgsMatcher.match(123, 123L), "Integer and Long should match");
        assertTrue(ArgsMatcher.match(UUID.fromString("d290f1ee-6c54-4b01-90e6-d701748f0851"), "d290f1ee-6c54-4b01-90e6-d701748f0851"));
    }

    @Test
    void testMatchDifferentValues() {
        assertFalse(ArgsMatcher.match(123, 456));
        assertFalse(ArgsMatcher.match("Hello", "World"));
    }

    @Test
    void testMatchNullValues() {
        assertTrue(ArgsMatcher.match(null, null));
        assertFalse(ArgsMatcher.match(null, "Non-null"));
    }

    @Test
    void testMatchArrays() {
        Object[] array1 = {1, 2, 3};
        Object[] array2 = {1L, 2, 3};

        assertTrue(ArgsMatcher.match(array1, array2));
    }

    @Test
    void testMatchDifferentArrays() {
        Object[] array1 = {1, 2};
        Object[] array2 = {1, 2, 3};

        assertFalse(ArgsMatcher.match(array1, array2));
    }

    @Test
    void testMatchWithPatternStrings() {
        assertTrue(ArgsMatcher.match("Hello", "hello"));
        assertTrue(ArgsMatcher.match("123abc", "\\d+abc"));
        assertFalse(ArgsMatcher.match("Hello", "world"));
    }

    @Test
    public void testArraysWithAny() {
        assertTrue(ArgsMatcher.match(List.of("1", 2), List.of("1", new MockUtilsTask.Any())));
    }
}
