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

import com.walmartlabs.concord.server.org.EntityOwner;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Reproduces the default JaVers snapshot-based set value-object identifier. */
final class AuditObjectHash {
    private AuditObjectHash() {
    }

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers()) && !field.isSynthetic()) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    static String hash(Object value) {
        List<String> states = new ArrayList<>();
        states.add(snapshot(value));
        // JaVers hashes only direct value-object descendants, not their descendants.
        for (Field field : FIELDS.get(value.getClass())) {
            Object child = get(field, value);
            if (child == null) {
                continue;
            }
            if (isManaged(field.getType())) {
                if (child != value) {
                    states.add(snapshot(child));
                }
            } else if (isManagedContainer(field)) {
                if (child instanceof Map<?, ?> map) {
                    for (Object item : map.values()) {
                        if (item != null && item != value) {
                            states.add(snapshot(item));
                        }
                    }
                } else if (child instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        if (item != null && item != value) {
                            states.add(snapshot(item));
                        }
                    }
                }
            }
        }
        Collections.sort(states);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\n", states).getBytes(StandardCharsets.UTF_8));
            byte[] selected = new byte[digest.length / 2];
            for (int i = 0; i < selected.length; i++) {
                selected[i] = digest[2 * i];
            }
            return HexFormat.of().formatHex(selected);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String snapshot(Object value) {
        Map<String, Object> state = new HashMap<>();
        for (Field field : FIELDS.get(value.getClass())) {
            if (isManaged(field.getType()) || isManagedContainer(field)) {
                continue;
            }
            Object item = get(field, value);
            if (item == null || (field.getType() == boolean.class && Boolean.FALSE.equals(item))
                    || (field.getType().isPrimitive() && item instanceof Number n && n.doubleValue() == 0)
                    || (field.getType() == char.class && ((Character) item) == 0)) {
                continue;
            }
            // SnapshotFactory converts CustomComparableType properties to their
            // JaVers value-to-string representation before JSON serialization.
            if (item instanceof OffsetDateTime date) {
                item = date.toLocalDateTime() + "," + date.getOffset();
            } else if (field.getType().isPrimitive() || item instanceof String
                    || item instanceof Number || item instanceof Boolean || item instanceof Character
                    || item instanceof UUID || item instanceof Enum<?>) {
                item = item.toString();
            } else if (item instanceof Set<?> set) {
                item = new TreeSet<>(set);
            }
            state.put(field.getName(), item);
        }
        StringBuilder json = new StringBuilder();
        appendJson(json, state, 0);
        return json.toString();
    }

    private static boolean isManaged(Class<?> type) {
        return EntityOwner.class.isAssignableFrom(type) || RepositoryEntry.class.isAssignableFrom(type)
                || ProjectEntry.class.isAssignableFrom(type);
    }

    private static boolean isManagedContainer(Field field) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType parameterized)) {
            return false;
        }
        for (Type argument : parameterized.getActualTypeArguments()) {
            if (argument instanceof Class<?> c && isManaged(c)) {
                return true;
            }
        }
        return false;
    }

    private static Object get(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // JaVers' default JSON converter uses Gson's two-space pretty printing and HTML-safe escaping.
    private static void appendJson(StringBuilder out, Object value, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof OffsetDateTime date) {
            quote(out, date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else if (value instanceof Date date) {
            quote(out, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault())));
        } else if (value instanceof CharSequence || value instanceof Character || value instanceof UUID
                || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            quote(out, value instanceof Enum<?> e ? e.name() : value.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                indent(out, depth + 1);
                quote(out, String.valueOf(entry.getKey()));
                out.append(": ");
                appendJson(out, entry.getValue(), depth + 1);
            }
            if (!first) {
                indent(out, depth);
            }
            out.append('}');
        } else if (value instanceof Collection<?> collection) {
            out.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                indent(out, depth + 1);
                appendJson(out, item, depth + 1);
            }
            if (!first) {
                indent(out, depth);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                indent(out, depth + 1);
                appendJson(out, Array.get(value, i), depth + 1);
            }
            if (length > 0) {
                indent(out, depth);
            }
            out.append(']');
        } else {
            // Gson serializes non-container values in Object-typed maps using their fields.
            Map<String, Object> fields = new java.util.LinkedHashMap<>();
            for (Field field : FIELDS.get(value.getClass())) {
                fields.put(field.getName(), get(field, value));
            }
            appendJson(out, fields, depth);
        }
    }

    private static void indent(StringBuilder out, int depth) {
        out.append('\n');
        out.append("  ".repeat(depth));
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\f' -> out.append("\\f");
                case '<', '>', '&', '=', '\'', '\u2028', '\u2029' -> out.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
