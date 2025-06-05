package com.walmartlabs.concord.runtime.common;

import java.util.*;

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
