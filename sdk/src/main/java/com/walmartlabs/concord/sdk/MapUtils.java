package com.walmartlabs.concord.sdk;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MapUtils {

    public static <E extends Enum<E>> E getEnum(Map<String, Object> m, String name, Class<E> enumData, E defaultValue) {
        String v = getString(m, name);
        if (v == null) {
            return defaultValue;
        }

        for (Enum<E> enumVal : enumData.getEnumConstants()) {
            if (enumVal.name().equals(v)) {
                return Enum.valueOf(enumData, v);
            }
        }
        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected one of: " +
                Arrays.stream(enumData.getEnumConstants()).map(Enum::name).collect(Collectors.toList()) +
                ", got: " + v);
    }

    public static UUID getUUID(Map<String, Object> m, String name) {
        Object o = m.get(name);
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return UUID.fromString((String) o);
        }
        if (o instanceof UUID) {
            return (UUID) o;
        }
        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: string/uuid, got: " + o.getClass());
    }

    public static String getString(Map<String, Object> m, HasKey k) {
        return getString(m, k.getKey());
    }

    public static String getString(Map<String, Object> m, String name) {
        return getString(m, name, null);
    }

    public static String getString(Map<String, Object> m, HasKey k, String defaultValue) {
        return getString(m, k.getKey(), defaultValue);
    }

    public static String getString(Map<String, Object> m, String name, String defaultValue) {
        return get(m, name, defaultValue, String.class);
    }

    public static <K, V> Map<K, V> getMap(Map<String, Object> m, HasKey k, Map<K, V> defaultValue) {
        return getMap(m, k.getKey(), defaultValue);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMap(Map<String, Object> m, String name, Map<K, V> defaultValue) {
        return get(m, name, defaultValue, Map.class);
    }

    public static <E> List<E> getList(Map<String, Object> m, HasKey k, List<E> defaultValue) {
        return getList(m, k.getKey(), defaultValue);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> getList(Map<String, Object> m, String name, List<E> defaultValue) {
        return get(m, name, defaultValue, List.class);
    }

    public static boolean getBoolean(Map<String, Object> m, HasKey k, boolean defaultValue) {
        return getBoolean(m, k.getKey(), defaultValue);
    }

    public static boolean getBoolean(Map<String, Object> m, String name, boolean defaultValue) {
        Boolean result = get(m, name, defaultValue, Boolean.class);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    public static int getInt(Map<String, Object> m, String name, int defaultValue) {
        Integer result = get(m, name, defaultValue, Integer.class);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    public static Number getNumber(Map<String, Object> m, String name, Number defaultValue) {
        return get(m, name, defaultValue, Number.class);
    }

    public static UUID assertUUID(Map<String, Object> m, String name) {
        UUID uuid = getUUID(m, name);
        if (uuid != null) {
            return uuid;
        }
        throw new IllegalArgumentException("Mandatory variable '" + name + "' is required");
    }

    public static int assertInt(Map<String, Object> m, String name) {
        return assertVariable(m, name, Integer.class);
    }

    public static Number assertNumber(Map<String, Object> m, String name) {
        return assertVariable(m, name, Number.class);
    }

    public static String assertString(Map<String, Object> m, String name) {
        return assertVariable(m, name, String.class);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> assertMap(Map<String, Object> m, String name) {
        return assertVariable(m, name, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> assertList(Map<String, Object> m, String name) {
        return assertVariable(m, name, List.class);
    }

    public static <T> T assertVariable(Map<String, Object> m, String name, Class<T> type) {
        T result = get(m, name, null, type);

        if (result != null) {
            return result;
        }

        throw new IllegalArgumentException("Mandatory variable '" + name + "' is required");
    }

    public static <T> T get(Map<String, Object> m, String name, T defaultValue, Class<T> type) {
        Object value = get(m, name, defaultValue);
        if (value == null) {
            return defaultValue;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: " + type + ", got: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Map<String, Object> m, String name, T defaultValue) {
        if (m == null) {
            return defaultValue;
        }

        return (T) m.getOrDefault(name, defaultValue);
    }

    private MapUtils() {
    }
}
