package com.walmartlabs.concord.server.events;

import java.util.Collection;
import java.util.Map;

public final class EventMatcher {

    public static boolean matches(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        return compareNodes(data, conditions);
    }

    @SuppressWarnings("unchecked")
    private static boolean compareNodes(Object data, Object conditions) {
        if (data == null && conditions == null) {
            return true;
        } else if (data == null || conditions == null) {
            return false;
        }

        Class<?> dataType = data.getClass();
        Class<?> conditionsType = conditions.getClass();

        if (!dataType.isAssignableFrom(conditionsType)) {
            return false;
        }

        if (conditions instanceof Map) {
            return compareObjectNodes((Map<String, Object>)data, (Map<String, Object>)conditions);
        } else if (conditions instanceof String) {
            return compareStringValues((String)data, (String)conditions);
        } else if (conditions instanceof Collection) {
            return compareArrayNodes((Collection)data, (Collection)conditions);
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
}
