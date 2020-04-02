package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.context.IntermediateGlobalsContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;

import java.util.*;

public final class Interpolator {

    @SuppressWarnings("unchecked")
    public static <T> T interpolate(ExpressionEvaluator ee, Context ctx, Object v, Class<T> expectedType) {
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            String s = (String) v;
            if (hasExpression(s)) {
                return ee.eval(ctx, s, expectedType);
            } else {
                return (T) v;
            }
        } else if (v instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) v;
            if (m.isEmpty()) {
                return (T) m;
            }

            // use LinkedHashMap to preserve the order of keys
            // which is important in cases when a value references another value in the same map
            Map<Object, Object> mm = new LinkedHashMap<>(m.size());
            return (T) interpolateMap(ee, ctx, m, mm);
        } else if (v instanceof List) {
            List<Object> src = (List<Object>) v;
            if (src.isEmpty()) {
                return (T) v;
            }

            List<Object> dst = new ArrayList<>(src.size());
            for (Object vv : src) {
                dst.add(interpolate(ee, ctx, vv, Object.class));
            }

            return (T) dst;
        } else if (v instanceof Set) {
            Set<Object> src = (Set<Object>) v;
            if (src.isEmpty()) {
                return (T) v;
            }

            // use LinkedHashSet to preserve the order of keys
            Set<Object> dst = new LinkedHashSet<>(src.size());
            for (Object vv : src) {
                dst.add(interpolate(ee, ctx, vv, Object.class));
            }

            return (T) dst;
        } else if (v.getClass().isArray()) {
            Object[] src = (Object[]) v;
            if (src.length == 0) {
                return (T) v;
            }

            for (int i = 0; i < src.length; i++) {
                src[i] = interpolate(ee, ctx, src[i], Object.class);
            }

            return (T) src;
        }

        return expectedType.cast(v);
    }

    public static boolean hasExpression(String s) {
        return s.contains("${");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<Object, Object> interpolateMap(ExpressionEvaluator ee, Context ctx, Map<Object, Object> v, Map container) {
        Context iCtx = new IntermediateGlobalsContext(ctx, new GlobalVariablesWithOverrides(ctx.globalVariables(), container));

        for (Map.Entry<?, ?> e : v.entrySet()) {
            Object kk = e.getKey();
            kk = interpolate(ee, iCtx, kk, Object.class);

            Object vv = e.getValue();
            vv = interpolate(ee, iCtx, vv, Object.class);

            container.put(kk, vv);
        }

        return container;
    }

    /**
     * Simple map-backed implementation of {@link GlobalVariables}.
     * Comparing to other implementations, this one doesn't do any defensive copying
     * so that the underlying map with override values can be modified externally.
     * All modifications are propagated to the delegated {@link GlobalVariables} instance.
     */
    private static class GlobalVariablesWithOverrides implements GlobalVariables {

        private static final long serialVersionUID = 1L;

        private final GlobalVariables delegate;
        private final Map<String, Object> overrides;

        private GlobalVariablesWithOverrides(GlobalVariables delegate, Map<String, Object> overrides) {
            this.delegate = delegate;
            this.overrides = overrides;
        }

        @Override
        public Object get(String key) {
            if (overrides.containsKey(key)) {
                return overrides.get(key);
            }
            return delegate.get(key);
        }

        @Override
        public void put(String key, Object value) {
            delegate.put(key, value);
        }

        @Override
        public void putAll(Map<String, Object> values) {
            delegate.putAll(values);
        }

        @Override
        public Object remove(String key) {
            return delegate.remove(key);
        }

        @Override
        public boolean containsKey(String key) {
            if (overrides.containsKey(key)) {
                return true;
            }
            return delegate.containsKey(key);
        }

        @Override
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>(delegate.toMap());
            result.putAll(overrides);
            return result;
        }
    }

    private Interpolator() {
    }
}
