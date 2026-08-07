package com.walmartlabs.concord.runtime.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SensitiveDataMasker {

    private static final String MASK = "******";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T mask(T v, Set<String> sensitiveStrings) {
        if (sensitiveStrings.isEmpty()) {
            return v;
        }

        if (v instanceof String s) {
            for (var sensitiveString : sensitiveStrings) {
                s = s.replace(sensitiveString, MASK);
            }
            return (T) s;
        } else if (v instanceof List<?> l) {
            var result = new ArrayList<>(l.size());
            for (var vv : l) {
                vv = mask(vv, sensitiveStrings);
                result.add(vv);
            }
            return (T) result;
        } else if (v instanceof Map m) {
            var result = new LinkedHashMap<>(m);
            result.replaceAll((k, vv) -> mask(vv, sensitiveStrings));
            return (T) result;
        } else if (v instanceof Set<?> s) {
            var result = new LinkedHashSet<>(s.size());
            for (var vv : s) {
                vv = mask(vv, sensitiveStrings);
                result.add(vv);
            }
            return (T) result;
        }

        return v;
    }
}
