package com.walmartlabs.concord.runtime.common;

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

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectTruncaterTest {

    @Test
    public void testTruncArray() {
        List<Integer> value = Arrays.asList(1, 2, 3, 4, 5);
        int maxStringLength = 3;
        int maxArrayLength = 2;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, "skipped 3 lines", 5), result);
    }

    @Test
    public void testTruncArray1() {
        int[] value = new int[]{1, 2, 3, 4, 5};
        int maxStringLength = 3;
        int maxArrayLength = 2;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);

        assertTrue(result.getClass().getComponentType().isPrimitive());
        assertArrayEquals(new int[]{1, 2}, (int[]) result);
    }

    @Test
    public void testTruncArray2() {
        List<Integer> value = Arrays.asList(1, 2, 3, 4, 5);
        int maxStringLength = 3;
        int maxArrayLength = 3;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, 2, "skipped 2 lines", 5), result);
    }

    @Test
    public void testTruncArray3() {
        List<Integer> value = Arrays.asList(1, 2, 3, 4, 5);
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, 2, "skipped 1 lines", 4, 5), result);
    }

    @Test
    public void testTruncArray4() {
        List<Integer> value = Arrays.asList(1, 2, 3);
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    @Test
    public void testTruncArray5() {
        List<Object> value = Arrays.asList(1, 2, Arrays.asList(11, 22, 33, 44, 55));
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, 2, Arrays.asList(11, 22, "skipped 1 lines", 44, 55)), result);
    }

    @Test
    public void testTruncArray6() {
        List<Object> value = Arrays.asList(1, 2, Arrays.asList(11, Arrays.asList(111, 222)));
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 1;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Arrays.asList(1, 2, Arrays.asList(11, Collections.singletonList("skipped: max depth reached"))), result);
    }

    @Test
    public void testTruncString() {
        Map<String, Object> value = new HashMap<>();
        value.put("k", "123456");
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Collections.<String, Object>singletonMap("k", "12...[skipped 3 chars]...6"), result);
    }

    @Test
    public void testTruncString2() {
        Map<String, Object> value = new HashMap<>();
        value.put("k", "123");
        int maxStringLength = 3;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Collections.<String, Object>singletonMap("k", "123"), result);
    }

    @Test
    public void testTruncString3() {
        Map<String, Object> value = new HashMap<>();
        value.put("k", "1234567890");
        int maxStringLength = 2;
        int maxArrayLength = 4;
        int maxDepth = 2;

        // ---
        Object result = ObjectTruncater.truncate(value, maxStringLength, maxArrayLength, maxDepth);
        assertEquals(Collections.<String, Object>singletonMap("k", "1...[skipped 8 chars]...0"), result);
    }
}
