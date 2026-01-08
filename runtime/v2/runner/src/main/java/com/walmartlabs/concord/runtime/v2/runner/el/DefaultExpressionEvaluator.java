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

import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.SensitiveDataProcessor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.CustomBeanMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.CustomTaskMethodResolver;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;

import javax.inject.Inject;
import java.util.*;

public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    private final LazyExpressionEvaluator delegate;

    @Inject
    public DefaultExpressionEvaluator(TaskProviders taskProviders,
                                      FunctionHolder functionHolder,
                                      List<CustomTaskMethodResolver> taskMethodResolvers,
                                      List<CustomBeanMethodResolver> beanMethodResolvers,
                                      SensitiveDataProcessor sensitiveDataProcessor) {
        this.delegate = new LazyExpressionEvaluator(taskProviders, functionHolder, taskMethodResolvers, beanMethodResolvers, sensitiveDataProcessor);
    }

    @Override
    public <T> T eval(EvalContext ctx, Object value, Class<T> expectedType) {
        Object result = delegate.eval(ctx, value, Object.class);
        return initializeAll(result, expectedType);
    }

    private static <T> T initializeAll(Object value, Class<T> expectedType) {
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            return expectedType.cast(initializeMap(m));
        } else if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            if (set.isEmpty()) {
                return expectedType.cast(new LinkedHashSet<>());
            }
            return expectedType.cast(initializeSet(set));
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                return expectedType.cast(new ArrayList<>());
            }
            return expectedType.cast(initializeList(collection));
        } else if (value != null && value.getClass().isArray()) {
            Object[] arr = (Object[])value;
            if (arr.length == 0) {
                return expectedType.cast(arr);
            }
            return expectedType.cast(initializeArray(arr));
        }
        return expectedType.cast(value);
    }

    private static Object[] initializeArray(Object[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = initializeAll(arr[i], Object.class);
        }
        return arr;
    }

    private static Map<Object, Object> initializeMap(Map<?, ?> value) {
        Map<Object, Object> result = new LinkedHashMap<>(value.size());
        for (Map.Entry<?, ?> e : value.entrySet()) {
            Object kk = e.getKey();
            kk = initializeAll(kk, Object.class);

            Object vv = e.getValue();
            vv = initializeAll(vv, Object.class);

            result.put(kk, vv);
        }
        return result;
    }

    private static List<Object> initializeList(Collection<?> value) {
        List<Object> result = new ArrayList<>(value.size());
        for (Object o : value) {
            result.add(initializeAll(o, Object.class));
        }
        return result;
    }

    private static Set<Object> initializeSet(Collection<?> value) {
        Set<Object> result = new LinkedHashSet<>(value.size());
        for (Object o : value) {
            result.add(initializeAll(o, Object.class));
        }
        return result;
    }
}
