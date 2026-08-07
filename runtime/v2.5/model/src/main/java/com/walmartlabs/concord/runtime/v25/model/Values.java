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

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Values {

    /**
     * Returns a recursively frozen copy of maps, collections, entries, and arrays.
     *
     * <p>Other leaf values, including Serializable task values, are retained by reference. Callers must treat
     * those leaf objects as immutable when sharing a frozen value between scopes.</p>
     */
    public static Object freeze(Object value) {
        return freeze(value, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, ?> value) {
        return (Map<String, Object>) freeze(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> list(List<T> value) {
        return (List<T>) freeze(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> set(Iterable<T> value) {
        var source = new ArrayList<T>();
        value.forEach(source::add);
        return (Set<T>) freeze(new LinkedHashSet<>(source));
    }

    public static boolean structurallyEqual(Object left, Object right) {
        return structurallyEqual(left, right, new IdentityHashMap<>());
    }

    private static Object freeze(Object value, IdentityHashMap<Object, Object> seen) {
        if (value == null) {
            return null;
        }
        var previous = seen.get(value);
        if (previous != null) {
            return previous;
        }
        if (value instanceof Map<?, ?> input) {
            var backing = new LinkedHashMap<Object, Object>(input.size());
            var result = Collections.unmodifiableMap(backing);
            seen.put(value, result);
            input.forEach((key, item) -> backing.put(freeze(key, seen), freeze(item, seen)));
            return result;
        }
        if (value instanceof List<?> input) {
            var backing = new ArrayList<>(input.size());
            var result = Collections.unmodifiableList(backing);
            seen.put(value, result);
            input.forEach(item -> backing.add(freeze(item, seen)));
            return result;
        }
        if (value instanceof Set<?> input) {
            var backing = new LinkedHashSet<>(input.size());
            var result = Collections.unmodifiableSet(backing);
            seen.put(value, result);
            input.forEach(item -> backing.add(freeze(item, seen)));
            return result;
        }
        if (value instanceof Collection<?> input) {
            var backing = new ArrayList<>(input.size());
            var result = Collections.unmodifiableList(backing);
            seen.put(value, result);
            input.forEach(item -> backing.add(freeze(item, seen)));
            return result;
        }
        if (value instanceof Map.Entry<?, ?> input) {
            var result = new AbstractMap.SimpleImmutableEntry<>(freeze(input.getKey(), seen),
                    freeze(input.getValue(), seen));
            seen.put(value, result);
            return result;
        }
        if (value.getClass().isArray()) {
            var length = Array.getLength(value);
            var result = Array.newInstance(value.getClass().getComponentType(), length);
            seen.put(value, result);
            if (value instanceof Object[] input) {
                for (var i = 0; i < length; i++) {
                    Array.set(result, i, freeze(input[i], seen));
                }
            } else {
                System.arraycopy(value, 0, result, 0, length);
            }
            return result;
        }
        return value;
    }

    private static boolean structurallyEqual(Object left, Object right,
                                             IdentityHashMap<Object, IdentityHashMap<Object, Equality>> seen) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        var state = state(left, right, seen);
        if (state == Equality.IN_PROGRESS || state == Equality.EQUAL) {
            return true;
        }
        if (state == Equality.NOT_EQUAL) {
            return false;
        }
        mark(left, right, Equality.IN_PROGRESS, seen);

        var equal = false;
        try {
            if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
                if (leftMap.size() != rightMap.size()) {
                    return false;
                }
                var candidates = new ArrayList<>(rightMap.entrySet());
                var matched = new boolean[candidates.size()];
                for (var leftEntry : leftMap.entrySet()) {
                    var found = false;
                    for (var i = 0; i < candidates.size(); i++) {
                        var rightEntry = candidates.get(i);
                        if (matched[i]) {
                            continue;
                        }
                        var candidateSeen = copySeen(seen);
                        if (structurallyEqual(leftEntry.getKey(), rightEntry.getKey(), candidateSeen)
                                && structurallyEqual(leftEntry.getValue(), rightEntry.getValue(), candidateSeen)) {
                            seen = candidateSeen;
                            matched[i] = true;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                equal = true;
                return true;
            }
            if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
                if (leftList.size() != rightList.size()) {
                    return false;
                }
                for (var i = 0; i < leftList.size(); i++) {
                    if (!structurallyEqual(leftList.get(i), rightList.get(i), seen)) {
                        return false;
                    }
                }
                equal = true;
                return true;
            }
            if (left instanceof Set<?> leftSet && right instanceof Set<?> rightSet) {
                if (leftSet.size() != rightSet.size()) {
                    return false;
                }
                var candidates = new ArrayList<>(rightSet);
                var matched = new boolean[candidates.size()];
                for (var leftItem : leftSet) {
                    var found = false;
                    for (var i = 0; i < candidates.size(); i++) {
                        if (matched[i]) {
                            continue;
                        }
                        var candidateSeen = copySeen(seen);
                        if (structurallyEqual(leftItem, candidates.get(i), candidateSeen)) {
                            seen = candidateSeen;
                            matched[i] = true;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                equal = true;
                return true;
            }
            if (left instanceof Map.Entry<?, ?> leftEntry && right instanceof Map.Entry<?, ?> rightEntry) {
                equal = structurallyEqual(leftEntry.getKey(), rightEntry.getKey(), seen)
                        && structurallyEqual(leftEntry.getValue(), rightEntry.getValue(), seen);
                return equal;
            }
            if (left.getClass().isArray() && right.getClass().isArray()) {
                if (left.getClass().getComponentType().isPrimitive()
                        != right.getClass().getComponentType().isPrimitive()) {
                    return false;
                }
                var length = Array.getLength(left);
                if (length != Array.getLength(right)) {
                    return false;
                }
                for (var i = 0; i < length; i++) {
                    if (!structurallyEqual(Array.get(left, i), Array.get(right, i), seen)) {
                        return false;
                    }
                }
                equal = true;
                return true;
            }
            equal = left.equals(right);
            return equal;
        } finally {
            if (equal) {
                mark(left, right, Equality.EQUAL, seen);
            } else {
                mark(left, right, Equality.NOT_EQUAL, seen);
                unmark(left, right, seen);
            }
        }
    }

    private static IdentityHashMap<Object, IdentityHashMap<Object, Equality>> copySeen(
            IdentityHashMap<Object, IdentityHashMap<Object, Equality>> seen) {
        var copy = new IdentityHashMap<Object, IdentityHashMap<Object, Equality>>();
        seen.forEach((left, rights) -> copy.put(left, new IdentityHashMap<>(rights)));
        return copy;
    }

    private static Equality state(Object left, Object right,
                                  IdentityHashMap<Object, IdentityHashMap<Object, Equality>> seen) {
        var rights = seen.get(left);
        return rights == null ? null : rights.get(right);
    }

    private static void mark(Object left, Object right, Equality state,
                             IdentityHashMap<Object, IdentityHashMap<Object, Equality>> seen) {
        seen.computeIfAbsent(left, ignored -> new IdentityHashMap<>()).put(right, state);
    }

    private static void unmark(Object left, Object right,
                               IdentityHashMap<Object, IdentityHashMap<Object, Equality>> seen) {
        var rights = seen.get(left);
        rights.remove(right);
        if (rights.isEmpty()) {
            seen.remove(left);
        }
    }

    private enum Equality {
        IN_PROGRESS,
        EQUAL,
        NOT_EQUAL
    }


    private Values() {
    }
}
