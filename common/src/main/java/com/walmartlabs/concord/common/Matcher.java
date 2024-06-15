package com.walmartlabs.concord.common;

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
import java.util.UUID;
import java.util.regex.Pattern;

public final class Matcher {

    public static boolean matches(Object data, Object conditions) {
        return compareNodes(data, conditions);
    }

    public static boolean matchAny(Object condition, Collection<Object> nodes) {
        for (Object n : nodes) {
            boolean result = compareNodes(n, condition);
            if (result) {
                return true;
            }
        }

        return false;
    }

    public static <T> boolean matchAny(Collection<T> conditions, T data) {
        for (T c : conditions) {
            boolean result = compareNodes(data, c);
            if (result) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compareNodes(Object data, Object conditions) {
        data = normalizeNull(data, conditions);

        if (data == null && conditions == null) {
            return true;
        } else if ((data == null && !(conditions instanceof Collection)) || conditions == null) {
            return false;
        }

        if (conditions instanceof Map && data instanceof Map) {
            return compareObjectNodes((Map<String, Object>) data, (Map<String, Object>) conditions);
        } else if (conditions instanceof String && data instanceof UUID) {
            return compareStringValues(data.toString(), (String)conditions);
        } else if (conditions instanceof String && data instanceof String) {
            return compareStringValues((String) data, (String) conditions);
        } else if (conditions instanceof Collection && data instanceof Collection) {
            return compareArrayNodes((Collection) data, (Collection) conditions);
        } else if (conditions instanceof Collection) {
            return matchAny((Collection) conditions, data);
        } else if (data instanceof Collection) {
            return matchAny(conditions, (Collection)data);
        } else {
            return compareValues(data, conditions);
        }
    }

    private static Object normalizeNull(Object data, Object conditions) {
        if (data != null) {
            return data;
        }

        if (conditions instanceof String) {
            return "";
        }

        return null;
    }

    private static boolean compareObjectNodes(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions.isEmpty() && !data.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> e : conditions.entrySet()) {
            Object dataItem = data.get(e.getKey());
            if (!compareNodes(dataItem, e.getValue())) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareStringValues(String value, String condition) {
        return Pattern.compile(condition, Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private static boolean compareArrayNodes(Collection<Object> dataElements, Collection<Object> conditionElements) {
        if (conditionElements.size() > dataElements.size()) {
            return false;
        }

        if (conditionElements.isEmpty() && !dataElements.isEmpty()) {
            return false;
        }

        for (Object c : conditionElements) {
            boolean matched = matchAny(c, dataElements);
            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareValues(Object dataValue, Object conditionValue) {
        return dataValue.equals(conditionValue);
    }

    private Matcher() {
    }
}
