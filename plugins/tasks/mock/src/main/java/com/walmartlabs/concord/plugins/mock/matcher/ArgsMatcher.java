package com.walmartlabs.concord.plugins.mock.matcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ArgsMatcher {

    private static final List<Matcher<?, ?>> MATCHERS = List.of(
            new MapMatcher(),
            new CollectionMatcher(),
            new StringValueMatcher(),
            new ValueMatcher()
    );

    public static boolean match(Object input, Object mockInput) {
        Object normalizedInput = normalizeValue(input);
        Object normalizedMockInput = normalizeValue(mockInput);

        return MATCHERS.stream()
                .filter(matcher -> matcher.canHandle(normalizedInput, normalizedMockInput))
                .findFirst()
                .map(matcher -> invokeMatch(matcher, normalizedInput, normalizedMockInput))
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static <E1, E2> boolean invokeMatch(Matcher<E1, E2> matcher, Object input, Object mockInput) {
        return matcher.matches((E1) input, (E2) mockInput);
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof UUID) {
            return value.toString();
        } else if (isArray(value)) {
            return Arrays.asList((Object[]) value);
        }
        return value;
    }

    private static boolean isArray(Object obj) {
        return obj != null && obj.getClass().isArray();
    }

    private ArgsMatcher() {}
}
