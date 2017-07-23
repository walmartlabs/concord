package com.walmartlabs.concord.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ConfigurationUtils {

    public static Object get(Map<String, Object> m, String... path) {
        int depth = path != null ? path.length : 0;
        return get(m, depth, path);
    }

    @SuppressWarnings("unchecked")
    public static Object get(Map<String, Object> m, int depth, String... path) {
        if (m == null) {
            return null;
        }

        if (depth == 0) {
            return m;
        }

        for (int i = 0; i < depth - 1; i++) {
            Object v = m.get(path[i]);
            if (v == null) {
                return null;
            }

            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid data type, expected JSON object, got: " + v.getClass());
            }

            m = (Map<String, Object>) v;
        }

        return m.get(path[path.length - 1]);
    }

    public static void set(Map<String, Object> a, Object b, String... path) {
        Object holder = get(a, path.length - 1, path);

        if (holder != null && !(holder instanceof Map)) {
            throw new IllegalArgumentException("Value should be contained in a JSON object: " + String.join("/", path));
        }

        Map<String, Object> m = (Map<String, Object>) holder;
        m.put(path[path.length - 1], b);
    }

    public static void delete(Map<String, Object> a, String... path) {
        Object holder = get(a, path.length - 1, path);

        if (holder != null && !(holder instanceof Map)) {
            throw new IllegalArgumentException("Value should be contained in a JSON object: " + String.join("/", path));
        }

        Map<String, Object> m = (Map<String, Object>) holder;
        m.remove(path[path.length - 1]);
    }

    @SuppressWarnings("unchecked")
    public static void merge(Map<String, Object> a, Map<String, Object> b, String... path) {
        Object holder = get(a, path);

        if (holder != null && !(holder instanceof Map)) {
            throw new IllegalArgumentException("Existing value is not a JSON object: " + holder + " @ " + String.join("/", path));
        }

        Map<String, Object> m = (Map<String, Object>) holder;
        m.putAll(b);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> result = new HashMap<>(a != null ? a : Collections.emptyMap());

        for (String k : b.keySet()) {
            Object av = result.get(k);
            Object bv = b.get(k);

            if (av instanceof Map && bv instanceof Map) {
                result.put(k, deepMerge((Map<String, Object>) av, (Map<String, Object>) bv));
            } else {
                result.put(k, bv);
            }
        }
        return result;
    }

    @SafeVarargs
    public static Map<String, Object> deepMerge(Map<String, Object>... maps) {
        if (maps == null || maps.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>(maps[0]);
        for (int i = 1; i < maps.length; i++) {
            result = deepMerge(result, maps[i]);
        }
        return result;
    }

    public static Map<String, Object> toNested(String k, Object v) {
        String[] as = k.split("\\.");
        if (as.length == 1) {
            return Collections.singletonMap(k, v);
        }

        Map<String, Object> m = new HashMap<>();
        Map<String, Object> root = m;

        for (int i = 0; i < as.length; i++) {
            if (i + 1 >= as.length) {
                m.put(as[i], v);
            } else {
                Map<String, Object> mm = new HashMap<>();
                m.put(as[i], mm);
                m = mm;
            }
        }

        return root;
    }


    private ConfigurationUtils() {
    }
}
