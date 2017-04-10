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
        for (String k : b.keySet()) {
            Object av = a.get(k);
            Object bv = b.get(k);

            if (av instanceof Map && bv instanceof Map) {
                a.put(k, deepMerge((Map<String, Object>) av, (Map<String, Object>) bv));
            } else {
                a.put(k, bv);
            }
        }
        return a;
    }

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

    private ConfigurationUtils() {
    }
}
