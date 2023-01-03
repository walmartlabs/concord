package com.walmartlabs.concord.runtime.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ObjectTruncater {

    private static final String MAX_DEPTH_MSG = "skipped: max depth reached";

    public static Map<String, Object> truncateMap(Map<String, Object> value, int maxStringLength, int maxArrayLength, int maxDepth) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyMap();
        }

        return truncMap(value, 0, maxStringLength, maxArrayLength, maxDepth);
    }

    public static Object truncate(Object value, int maxStringLength, int maxArrayLength, int maxDepth) {
        if (value == null) {
            return null;
        }

        return trunc(value, 0, maxStringLength, maxArrayLength, maxDepth);
    }

    @SuppressWarnings("unchecked")
    private static Object trunc(Object value, int currentDepth, int maxStringLength, int maxArrayLength, int maxDepth) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) value;
            if (currentDepth > maxDepth) {
                return Collections.singletonMap("_", MAX_DEPTH_MSG);
            }
            return truncMap(m, currentDepth, maxStringLength, maxArrayLength, maxDepth);
        } else if (value instanceof Collection) {
            Collection<Object> l = (Collection<Object>) value;
            if (l.isEmpty()) {
                return l;
            }

            if (currentDepth > maxDepth) {
                return Collections.singletonList(MAX_DEPTH_MSG);
            }

            return truncArray(l.size(), l::stream, currentDepth, maxStringLength, maxArrayLength, maxDepth);
        } else if (value.getClass().isArray()) {
            if (value.getClass().getComponentType().isPrimitive()) {
                int size = Array.getLength(value);
                if (size == 0) {
                    return value;
                }

                if (currentDepth > maxDepth) {
                    return new String[]{MAX_DEPTH_MSG};
                }

                return truncPrimitiveArray(size, value, maxArrayLength);
            }

            Object[] src = (Object[]) value;
            if (src.length == 0) {
                return src;
            }

            if (currentDepth > maxDepth) {
                return new String[]{MAX_DEPTH_MSG};
            }
            return truncArray(src.length, () -> Stream.of(src), currentDepth, maxStringLength, maxArrayLength, maxDepth);
        } else if (value instanceof String) {
            return truncString((String) value, maxStringLength);
        }

        return value;
    }

    private static <K> Map<K, Object> truncMap(Map<K, Object> value, int currentDepth, int maxStringLength, int maxArrayLength, int maxDepth) {
        if (value.isEmpty()) {
            return value;
        }

        Map<K, Object> result = new HashMap<>();
        for (Map.Entry<K, Object> e : value.entrySet()) {
            result.put(e.getKey(), trunc(e.getValue(), currentDepth + 1, maxStringLength, maxArrayLength, maxDepth));
        }
        return result;
    }

    private static Collection<Object> truncArray(int size, Supplier<Stream<Object>> valueStreamSupplier,
                                                 int currentDepth, int maxStringLength, int maxArrayLength, int maxDepth) {
        if (size > maxArrayLength) {
            int maxOverLimit = maxArrayLength / 10;
            int overLimit = size - maxArrayLength;
            if (overLimit > maxOverLimit) {
                int halfMax = halfSize(maxArrayLength);
                return Stream.concat(
                        valueStreamSupplier.get().limit(halfMax)
                                .map(v -> trunc(v, currentDepth + 1, maxStringLength, maxArrayLength, maxDepth)),
                        Stream.concat(
                                Stream.of("skipped " + overLimit + " lines"),
                                valueStreamSupplier.get().skip(halfMax + (long) overLimit)
                                        .map(v -> trunc(v, currentDepth + 1, maxStringLength, maxArrayLength, maxDepth))))
                        .collect(Collectors.toList());
            }
        }

        return valueStreamSupplier.get()
                .map(v -> trunc(v, currentDepth + 1, maxStringLength, maxArrayLength, maxDepth))
                .collect(Collectors.toList());
    }

    private static Object truncPrimitiveArray(int size, Object array, int maxArrayLength) {

        if (size > maxArrayLength) {
            Object result = Array.newInstance(array.getClass().getComponentType(), maxArrayLength);
            for (int i = 0; i < maxArrayLength; i++) {
                Array.set(result, i, Array.get(array, i));
            }
            return result;
        }

        return array;
    }

    private static String truncString(String value, int maxStringLength) {
        if (value.length() <= maxStringLength) {
            return value;
        }

        int halfMax = halfSize(maxStringLength);
        int overlimit = value.length() - maxStringLength;
        return value.substring(0, halfMax) +
                "...[skipped " + overlimit + " chars]..." +
                value.substring(halfMax + overlimit);
    }

    private static int halfSize(int size) {
        int result = size / 2;
        if (result + result != size) {
            result += 1;
        }
        return result;
    }

    private ObjectTruncater() {
    }
}
