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

public final class MapMatcher {

    public static boolean matches(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        return compareNodes(data, conditions);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compareNodes(Object data, Object conditions) {
        if (data == null && conditions == null) {
            return true;
        } else if (data == null && conditions instanceof String) {
            return compareStringValues("", (String) conditions);
        } else if (data == null || conditions == null) {
            return false;
        }

        if (conditions instanceof Map && data instanceof Map) {
            return compareObjectNodes((Map<String, Object>) data, (Map<String, Object>) conditions);
        } else if (conditions instanceof String && data instanceof String) {
            return compareStringValues((String) data, (String) conditions);
        } else if (conditions instanceof Collection && data instanceof Collection) {
            return compareArrayNodes((Collection) data, (Collection) conditions);
        } else if (conditions instanceof Collection) {
            return matchAny(data, (Collection) conditions);
        } else {
            return compareValues(data, conditions);
        }
    }

    private static boolean compareObjectNodes(Map<String, Object> data, Map<String, Object> conditions) {
        for (String fieldName : conditions.keySet()) {
            Object dataItem = data.get(fieldName);
            Object conditionItem = conditions.get(fieldName);
            if (!compareNodes(dataItem, conditionItem)) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareArrayNodes(Collection<Object> dataElements, Collection<Object> conditionElements) {
        if (conditionElements.size() > dataElements.size()) {
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

    private static boolean matchAny(Object condition, Collection<Object> nodes) {
        for (Object n : nodes) {
            boolean result = compareNodes(n, condition);
            if (result) {
                return true;
            }
        }

        return false;
    }

    private static boolean compareStringValues(String dataValue, String conditionValue) {
        return dataValue.matches(conditionValue);
    }

    private static boolean compareValues(Object dataValue, Object conditionValue) {
        return dataValue.equals(conditionValue);
    }

    private MapMatcher() {
    }
}
