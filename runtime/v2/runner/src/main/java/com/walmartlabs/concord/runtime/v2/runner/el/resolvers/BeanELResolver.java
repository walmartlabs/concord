package com.walmartlabs.concord.runtime.v2.runner.el.resolvers;

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

import com.sun.el.util.ReflectionUtil;
import com.walmartlabs.concord.runtime.v2.runner.el.MethodNotFoundException;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.LazyValue;

import javax.el.ELContext;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Same as {@link javax.el.BeanELResolver}, but throws more detailed "method is not found" exceptions.
 */
public class BeanELResolver extends javax.el.BeanELResolver {

    private final Context context;
    private final SensitiveDataProcessor sensitiveDataProcessor;

    public BeanELResolver(Context context, SensitiveDataProcessor sensitiveDataProcessor) {
        this.context = context;
        this.sensitiveDataProcessor = sensitiveDataProcessor;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (base == null || method == null) {
            return null; // same as original javax.el.BeanELResolver
        }

        try {
            if (base instanceof LazyValue<?> lv) {
                base = lv.resolve(this.context);
            }

            params = resolveLazyValues(this.context, params);
            paramTypes = null;

            // NPE in super.invoke if method not found :(
            if (ReflectionUtil.findMethod(base.getClass(), method.toString(), paramTypes, params) == null) {
                throw new MethodNotFoundException(base.getClass(), method, paramTypes);
            }

            var result = super.invoke(context, base, method, paramTypes, params);

            if (context.isPropertyResolved()) {
                Method m = ReflectionUtil.findMethod(base.getClass(), method.toString(), paramTypes, params);
                sensitiveDataProcessor.process(result, m);
            }

            return result;
        } catch (javax.el.MethodNotFoundException e) {
            throw new MethodNotFoundException(base.getClass(), method, paramTypes);
        }
    }

    private static Object[] resolveLazyValues(Context context, Object[] params) {
        if (params == null) {
            return null;
        }

        var result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = resolveLazyValue(context, params[i]);
        }
        return result;
    }

    private static Object resolveLazyValue(Context context, Object value) {
        if (value instanceof Map<?, ?> m) {
            return initializeMap(context, m);
        } else if (value instanceof Set<?> set) {
            if (set.isEmpty()) {
                return set;
            }
            return initializeSet(context, set);
        } else if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return collection;
            }
            return initializeList(context, collection);
        } else if (value != null && value.getClass().isArray()) {
            var arr = (Object[])value;
            if (arr.length == 0) {
                return arr;
            }
            return initializeArray(context, arr);
        } else if (value instanceof LazyValue<?> v) {
            return v.resolve(context);
        }
        return value;
    }

    private static Object[] initializeArray(Context context, Object[] arr) {
        for (var i = 0; i < arr.length; i++) {
            arr[i] = resolveLazyValue(context, arr[i]);
        }
        return arr;
    }

    private static Map<Object, Object> initializeMap(Context context, Map<?, ?> value) {
        Map<Object, Object> result = new LinkedHashMap<>(value.size());
        for (var e : value.entrySet()) {
            var kk = e.getKey();
            kk = resolveLazyValue(context, kk);

            var vv = e.getValue();
            vv = resolveLazyValue(context, vv);

            result.put(kk, vv);
        }
        return result;
    }

    private static List<Object> initializeList(Context context, Collection<?> value) {
        List<Object> result = new ArrayList<>(value.size());
        for (var o : value) {
            result.add(resolveLazyValue(context, o));
        }
        return result;
    }

    private static Set<Object> initializeSet(Context context, Collection<?> value) {
        Set<Object> result = new LinkedHashSet<>(value.size());
        for (var o : value) {
            result.add(resolveLazyValue(context, o));
        }
        return result;
    }
}
