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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ArgUtils {

    public static Path getPath(Map<String, Object> args, String key, Path workDir) {
        Path p = null;

        Object v = args.get(key);
        if (v instanceof String) {
            p = workDir.resolve((String) v);
        } else if (v instanceof Path) {
            p = workDir.resolve((Path) v);
        } else if (v != null) {
            throw new IllegalArgumentException("'" + key + "' should be either a relative path: " + v);
        }

        if (p != null && !Files.exists(p)) {
            throw new IllegalArgumentException("File not found: " + workDir.relativize(p));
        }

        return p;
    }

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

    public static <K, V> Map<K, V> getMap(Map<String, Object> args, TaskParams c) {
        return getMap(args, c.getKey());
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMap(Map<String, Object> args, String key) {
        return (Map<K, V>) args.get(key);
    }

    public static boolean getBoolean(Map<String, Object> args, TaskParams c, boolean defaultValue) {
        Object v = args.get(c.getKey());
        if (v == null) {
            return defaultValue;
        }

        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        } else {
            throw new IllegalArgumentException("Invalid boolean value '" + v + "' for key '" + c.getKey() + "'");
        }
    }

    public static int getInt(Map<String, Object> args, TaskParams c, int defaultValue) {
        return getInt(args, c.getKey(), defaultValue);
    }

    public static int getInt(Map<String, Object> args, String c, int defaultValue) {
        Object v = args.get(c);
        if (v == null) {
            return defaultValue;
        }

        if (v instanceof Integer) {
            return (Integer) v;
        }

        if (v instanceof Long) {
            return ((Long) v).intValue();
        }

        throw new IllegalArgumentException("'" + c + "' should be an integer: " + v);
    }

    public static String assertString(String assertionMessage, Map<String, Object> args, String key) {
        String v = getString(args, key);
        if (v == null) {
            throw new IllegalArgumentException(assertionMessage);
        }
        return v;
    }

    public static String getString(Map<String, Object> args, TaskParams c) {
        return getString(args, c.getKey());
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

    public static <E> List<E> getList(Map<String, Object> args, TaskParams p) {
        return getList(args, p.getKey());
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> getList(Map<String, Object> args, String key) {
        Object v = args.get(key);

        if (v == null) {
            return null;
        }

        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Expected a list value '" + key + "', got: " + v);
        }

        return (List<E>) v;
    }

    @SuppressWarnings("unchecked")
    public static String getListAsString(Map<String, Object> args, TaskParams c) {
        Object v = args.get(c.getKey());
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return ((String) v).trim();
        }

        if (v instanceof Collection) {
            return String.join(", ", (Collection<String>) v);
        }

        throw new IllegalArgumentException("unexpected '" + c.getKey() + "' type: " + v);
    }

    private ArgUtils() {
    }
}
