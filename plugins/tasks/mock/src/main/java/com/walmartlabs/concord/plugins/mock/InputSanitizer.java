package com.walmartlabs.concord.plugins.mock;

import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.*;
import java.util.stream.Collectors;

public final class InputSanitizer {

    private static final Set<Class<?>> INPUT_VARIABLE_TYPES = Set.of(
            String.class, Boolean.class, Character.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class);

    public static List<Object> sanitize(List<Object> input) {
        var result = new ArrayList<>(input.size());
        for (var item : input) {
            result.add(sanitizeValue(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value.getClass().isPrimitive() || INPUT_VARIABLE_TYPES.contains(value.getClass())) {
            return value;
        }

        if (value instanceof Set<?> setValue) {
            return setValue.stream()
                    .map(InputSanitizer::sanitizeValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } else if (value instanceof Collection<?> collectionValue) {
            return collectionValue.stream()
                    .map(InputSanitizer::sanitizeValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else if (value instanceof Map) {
            Map<String, Object> mapValue = (Map<String, Object>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            mapValue.forEach((k, v) -> result.put(k, sanitizeValue(v)));
            return result;
        } else if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value)
                    .map(InputSanitizer::sanitizeValue)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (value instanceof Variables variables) {
            return sanitizeValue(variables.toMap());
        }

        return null;
    }

    private InputSanitizer() {
    }
}
