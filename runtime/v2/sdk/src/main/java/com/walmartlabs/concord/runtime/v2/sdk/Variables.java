package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface Variables {

    Object get(String key);

    void set(String key, Object value);

    boolean has(String key);

    Map<String, Object> toMap();

    default String getString(String key, String defaultValue) {
        return get(key, defaultValue, String.class);
    }

    default String assertString(String key) {
        return assertVariable(key, String.class);
    }

    default Number getNumber(String key, Number defaultValue) {
        return get(key, defaultValue, Number.class);
    }

    default boolean getBoolean(String key, boolean defaultValue) {
        Boolean result = get(key, defaultValue, Boolean.class);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    default int getInt(String key, int defaultValue) {
        return getNumber(key, defaultValue).intValue();
    }

    default long getLong(String key, long defaultValue) {
        return getNumber(key, defaultValue).longValue();
    }

    @SuppressWarnings("unchecked")
    default  <E> Collection<E> getCollection(String key, List<E> defaultValue) {
        return get(key, defaultValue, Collection.class);
    }

    @SuppressWarnings("unchecked")
    default <K, V> Map<K, V> getMap(String key, Map<K, V> defaultValue) {
        return get(key, defaultValue, Map.class);
    }

    default  <T> T assertVariable(String key, Class<T> type) {
        T result = get(key, null, type);

        if (result != null) {
            return result;
        }

        throw new IllegalArgumentException("Mandatory variable '" + key + "' is required");
    }

    default <T> T get(String key, T defaultValue, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return defaultValue;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        throw new IllegalArgumentException("Invalid variable '" + key + "' type, expected: " + type + ", got: " + value.getClass());
    }
}
