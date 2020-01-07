package com.walmartlabs.concord.server.events;

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

import java.util.*;

public final class ExpressionUtils {

    public static Map<String, Object> escapeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return map;
        }

        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> e : map.entrySet()) {
            result.put(e.getKey(), escape(e.getValue()));
        }
        return result;
    }

    public static String escapeString(String value) {
        if (value == null) {
            return null;
        }

        if (hasExpression(value)) {
            return value.replace("${", "\\${");
        }
        return value;
    }

    public static List<Object> escapeList(List<Object> value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        List<Object> dst = new ArrayList<>(value.size());
        for (Object vv : value) {
            dst.add(escape(vv));
        }

        return dst;
    }

    public static Set<Object> escapeSet(Set<Object> value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Set<Object> dst = new HashSet<>(value.size());
        for (Object vv : value) {
            dst.add(escape(vv));
        }

        return dst;
    }

    public static Object[] escapeArray(Object[] value) {
        if (value == null || value.length == 0) {
            return value;
        }

        for (int i = 0; i < value.length; i++) {
            value[i] = escape(value[i]);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object escape(Object value) {
        if (value instanceof String) {
            return escapeString((String) value);
        } else if (value instanceof Map) {
            return escapeMap((Map<String, Object>) value);
        } else if (value instanceof List) {
            return escapeList((List<Object>) value);
        } else if (value instanceof Set) {
            return escapeSet((Set<Object>) value);
        } else if (value instanceof Object[]) {
            return escapeArray((Object[]) value);
        }
        return value;
    }

    public static boolean hasExpression(String s) {
        return s.contains("${");
    }

    private ExpressionUtils() {
    }
}
