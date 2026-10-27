package com.walmartlabs.concord.server.org.project;

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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DiffUtils {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false, byte.class, (byte) 0, short.class, (short) 0,
            int.class, 0, long.class, 0L, float.class, 0F, double.class, 0D, char.class, '\0');

    public static Map<String, Object> compare(Object left, Object right) {
        Map<String, Object> previous = new HashMap<>();
        Map<String, Object> next = new HashMap<>();
        compareObject(left, right, previous, next, new IdentityHashMap<>(), new IdentityHashMap<>());

        Map<String, Object> result = new HashMap<>();
        if (!previous.isEmpty()) {
            result.put("prev", previous);
        }
        if (!next.isEmpty()) {
            result.put("new", next);
        }
        return result;
    }

    private static void compareObject(Object left, Object right, Map<String, Object> previous,
                                      Map<String, Object> next, IdentityHashMap<Object, Boolean> leftPath,
                                      IdentityHashMap<Object, Boolean> rightPath) {
        if (left == null && right == null) {
            return;
        }
        // Graphs can share objects or contain cycles. Only suppress a visit on the current
        // path, not another occurrence of the same object at a different audit path.
        if ((left != null && leftPath.containsKey(left)) || (right != null && rightPath.containsKey(right))) {
            return;
        }
        if (left != null) {
            leftPath.put(left, Boolean.TRUE);
        }
        if (right != null) {
            rightPath.put(right, Boolean.TRUE);
        }
        try {
            Class<?> type = left != null ? left.getClass() : right.getClass();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object oldValue = left == null ? null : fieldValue(field, left);
                    Object newValue = right == null ? null : fieldValue(field, right);
                    if (field.getType().isPrimitive() && (left == null || right == null)
                            && Objects.equals(left == null ? newValue : oldValue, PRIMITIVE_DEFAULTS.get(field.getType()))) {
                        continue;
                    }
                    compareProperty(field.getName(), oldValue, newValue, previous, next, leftPath, rightPath);
                }
            }
        } finally {
            if (left != null) {
                leftPath.remove(left);
            }
            if (right != null) {
                rightPath.remove(right);
            }
        }
    }

    private static Object fieldValue(Field field, Object object) {
        try {
            return field.get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read audit property " + field, e);
        }
    }

    private static void compareProperty(String name, Object left, Object right, Map<String, Object> previous,
                                        Map<String, Object> next, IdentityHashMap<Object, Boolean> leftPath,
                                        IdentityHashMap<Object, Boolean> rightPath) {
        Object value = left != null ? left : right;
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?>) {
            compareMap(name, (Map<?, ?>) left, (Map<?, ?>) right, previous, next, leftPath, rightPath);
        } else if (value instanceof List<?> || value.getClass().isArray()) {
            compareIndexed(name, left, right, previous, next, leftPath, rightPath);
        } else if (value instanceof Set<?>) {
            compareSet(name, (Set<?>) left, (Set<?>) right, previous, next, leftPath, rightPath);
        } else if (value instanceof Collection<?>) {
            // Collection membership changes themselves were not included in the audit map.
        } else if (isValue(value)) {
            if (!Objects.equals(left, right)) {
                if (left != null) {
                    previous.put(name, left);
                }
                if (right != null) {
                    next.put(name, right);
                }
            }
        } else {
            Map<String, Object> oldChild = new HashMap<>();
            Map<String, Object> newChild = new HashMap<>();
            compareObject(left, right, oldChild, newChild, leftPath, rightPath);
            mergeChild(previous, name, oldChild);
            mergeChild(next, name, newChild);
        }
    }

    private static void compareIndexed(String name, Object left, Object right,
                                       Map<String, Object> previous, Map<String, Object> next,
                                       IdentityHashMap<Object, Boolean> leftPath, IdentityHashMap<Object, Boolean> rightPath) {
        Map<String, Object> oldEntries = new HashMap<>();
        Map<String, Object> newEntries = new HashMap<>();
        int oldSize = size(left);
        int newSize = size(right);
        for (int i = 0; i < Math.max(oldSize, newSize); i++) {
            Object oldItem = i < oldSize ? item(left, i) : null;
            Object newItem = i < newSize ? item(right, i) : null;
            compareMapEntry(i, oldItem, newItem, oldEntries, newEntries, leftPath, rightPath);
        }
        mergeChild(previous, name, oldEntries);
        mergeChild(next, name, newEntries);
    }

    private static void compareSet(String name, Set<?> left, Set<?> right,
                                   Map<String, Object> previous, Map<String, Object> next,
                                   IdentityHashMap<Object, Boolean> leftPath, IdentityHashMap<Object, Boolean> rightPath) {
        compareMap(name, setObjects(left), setObjects(right), previous, next, leftPath, rightPath);
    }

    private static Map<String, Object> setObjects(Set<?> values) {
        Map<String, Object> result = new HashMap<>();
        if (values != null) {
            for (Object value : values) {
                if (isObjectReference(value)) {
                    result.put(AuditObjectHash.hash(value), value);
                }
            }
        }
        return result;
    }

    private static boolean isObjectReference(Object value) {
        return value != null && !isValue(value) && !(value instanceof Map<?, ?>)
                && !(value instanceof Collection<?>) && !value.getClass().isArray();
    }

    private static int size(Object value) {
        if (value == null) {
            return 0;
        }
        return value instanceof List<?> list ? list.size() : Array.getLength(value);
    }

    private static Object item(Object value, int index) {
        return value instanceof List<?> list ? list.get(index) : Array.get(value, index);
    }

    private static void compareMap(String name, Map<?, ?> left, Map<?, ?> right,
                                   Map<String, Object> previous, Map<String, Object> next,
                                   IdentityHashMap<Object, Boolean> leftPath, IdentityHashMap<Object, Boolean> rightPath) {
        Map<String, Object> oldEntries = new HashMap<>();
        Map<String, Object> newEntries = new HashMap<>();
        if (left != null) {
            for (Map.Entry<?, ?> entry : left.entrySet()) {
                compareMapEntry(entry.getKey(), entry.getValue(), right == null ? null : right.get(entry.getKey()),
                        oldEntries, newEntries, leftPath, rightPath);
            }
        }
        if (right != null) {
            for (Map.Entry<?, ?> entry : right.entrySet()) {
                if (left == null || !left.containsKey(entry.getKey())) {
                    compareMapEntry(entry.getKey(), null, entry.getValue(), oldEntries, newEntries, leftPath, rightPath);
                }
            }
        }
        mergeChild(previous, name, oldEntries);
        mergeChild(next, name, newEntries);
    }

    @SuppressWarnings("unchecked")
    private static void mergeChild(Map<String, Object> target, String name, Map<String, Object> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Object existing = target.get(name);
        if (existing instanceof Map<?, ?>) {
            Map<String, Object> existingMap = (Map<String, Object>) existing;
            changes.forEach((key, value) -> {
                if (value instanceof Map<?, ?>) {
                    mergeChild(existingMap, key, (Map<String, Object>) value);
                } else {
                    existingMap.put(key, value);
                }
            });
        } else {
            target.put(name, changes);
        }
    }

    private static void compareMapEntry(Object key, Object left, Object right,
                                        Map<String, Object> previous, Map<String, Object> next,
                                        IdentityHashMap<Object, Boolean> leftPath, IdentityHashMap<Object, Boolean> rightPath) {
        Object value = left != null ? left : right;
        if (value == null || isValue(value) || value instanceof Map<?, ?>
                || value instanceof Collection<?> || value.getClass().isArray()) {
            return;
        }
        compareProperty(String.valueOf(key), left, right, previous, next, leftPath, rightPath);
    }

    private static boolean isValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof UUID || value instanceof Enum<?>
                || value instanceof TemporalAccessor || value instanceof java.util.Date;
    }
}

