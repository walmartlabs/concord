package com.walmartlabs.concord.plugins.ansible;

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

import java.util.Collection;
import java.util.Map;

public final class ArgUtils {

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> assertMap(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }

        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected an object '" + key + ", got: " + v);
        }

        return (Map<K, V>) v;
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMap(Map<String, Object> args, String key) {
        return (Map<K, V>) args.get(key);
    }

    public static boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        } else {
            throw new IllegalArgumentException("Invalid boolean value '" + v + "' for key '" + key + "'");
        }
    }

    public static int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (v instanceof Integer) {
            return (Integer) v;
        }

        if (v instanceof Long) {
            return ((Long) v).intValue();
        }

        throw new IllegalArgumentException("'" + key + "' should be an integer: " + v);
    }

    public static String getString(Map<String, Object> args, String key) {
        Object v = args.get(key);

        if (v == null) {
            return null;
        }

        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Expected a string value '" + key + "', got: " + v);
        }

        return ((String) v).trim();
    }

    @SuppressWarnings("unchecked")
    public static String getListAsString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return ((String) v).trim();
        }

        if (v instanceof Collection) {
            return String.join(", ", (Collection<String>) v);
        }

        throw new IllegalArgumentException("unexpected '" + key + "' type: " + v);
    }

    private ArgUtils() {
    }
}
