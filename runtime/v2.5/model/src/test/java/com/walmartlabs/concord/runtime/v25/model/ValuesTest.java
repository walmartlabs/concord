package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesTest {

    @Test
    void freezesEveryJvmArrayTypeAndComparesContentsStructurally() {
        var arrays = new Object[]{
                new boolean[]{true}, new byte[]{1}, new char[]{'a'}, new short[]{2},
                new int[]{3}, new long[]{4L}, new float[]{5.0f}, new double[]{6.0d},
                new String[]{"value"}, new int[][]{{7}}
        };

        for (var array : arrays) {
            var frozen = Values.freeze(array);
            assertNotSame(array, frozen);
            assertTrue(Values.structurallyEqual(array, frozen));
        }

        assertFalse(Values.structurallyEqual(new int[]{1}, new int[]{2}));
        assertFalse(Values.structurallyEqual(new int[]{1}, new Integer[]{1}));
        assertTrue(Values.structurallyEqual(new int[][]{{1, 2}}, new int[][]{{1, 2}}));
    }

    @Test
    void doesNotReuseFailedCandidatesWhenMatchingSets() {
        var left = new LinkedHashSet<>(List.of(List.of("x", 0), List.of("x", 1)));
        var right = new LinkedHashSet<>(List.of(List.of("y", 1), List.of("x", 0)));

        assertFalse(Values.structurallyEqual(left, right));
        assertFalse(Values.structurallyEqual(List.of(List.of("x", 0), List.of("x", 1)),
                List.of(List.of("y", 1), List.of("x", 0))));
    }

    @Test
    void comparesCyclicValuesWithoutRecursingForever() {
        var left = new ArrayList<Object>();
        left.add(left);
        left.add("same");
        var equal = new ArrayList<Object>();
        equal.add(equal);
        equal.add("same");
        var different = new ArrayList<Object>();
        different.add(different);
        different.add("different");

        assertTrue(Values.structurallyEqual(left, equal));
        assertFalse(Values.structurallyEqual(left, different));
    }

    @Test
    void rejectsUnequalCyclicSetsAndDeepMergesCyclicMaps() {
        var left = new LinkedHashSet<Object>();
        left.add(left);
        left.add("left");
        var right = new LinkedHashSet<Object>();
        right.add(right);
        right.add("right");

        assertFalse(Values.structurallyEqual(left, right));

        var base = new LinkedHashMap<String, Object>();
        base.put("self", base);
        var overlay = new LinkedHashMap<String, Object>();
        overlay.put("self", overlay);
        var merged = Definition25.deepMerge(base, overlay);

        assertSame(merged, merged.get("self"));
    }

    @Test
    void freezesNonStandardCollections() {
        var source = new ArrayDeque<>(List.of("before"));

        var frozen = assertInstanceOf(List.class, Values.freeze(source));
        source.add("after");

        assertEquals(List.of("before"), frozen);
    }
}
