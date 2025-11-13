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
import com.walmartlabs.concord.runtime.v2.sdk.*;

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
        var result = delegate.eval(ctx, value, Object.class);
        return initializeAll(ctx, result, expectedType);
    }

    private static <T> T initializeAll(EvalContext evalContext, Object value, Class<T> expectedType) {
        if (value instanceof Map<?, ?> m) {
            return expectedType.cast(initializeMap(evalContext, m));
        } else if (value instanceof Set<?> set) {
            if (set.isEmpty()) {
                return expectedType.cast(new LinkedHashSet<>());
            }
            return expectedType.cast(initializeSet(evalContext, set));
        } else if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return expectedType.cast(new ArrayList<>());
            }
            return expectedType.cast(initializeList(evalContext, collection));
        } else if (value != null && value.getClass().isArray()) {
            var arr = (Object[])value;
            if (arr.length == 0) {
                return expectedType.cast(arr);
            }
            return expectedType.cast(initializeArray(evalContext, arr));
        } else if (evalContext.resolveLazyValues() && value instanceof LazyValue<?> v) {
            var resolved = v.resolve(evalContext.context());
            return expectedType.cast(resolved);
        }
        return expectedType.cast(value);
    }

    private static Object[] initializeArray(EvalContext evalContext, Object[] arr) {
        for (var i = 0; i < arr.length; i++) {
            arr[i] = initializeAll(evalContext, arr[i], Object.class);
        }
        return arr;
    }

    private static Map<Object, Object> initializeMap(EvalContext evalContext, Map<?, ?> value) {
        Map<Object, Object> result = new LinkedHashMap<>(value.size());
        for (var e : value.entrySet()) {
            var kk = e.getKey();
            kk = initializeAll(evalContext, kk, Object.class);

            var vv = e.getValue();
            vv = initializeAll(evalContext, vv, Object.class);

            result.put(kk, vv);
        }
        return result;
    }

    private static List<Object> initializeList(EvalContext evalContext, Collection<?> value) {
        List<Object> result = new ArrayList<>(value.size());
        for (var o : value) {
            result.add(initializeAll(evalContext, o, Object.class));
        }
        return result;
    }

    private static Set<Object> initializeSet(EvalContext evalContext, Collection<?> value) {
        Set<Object> result = new LinkedHashSet<>(value.size());
        for (var o : value) {
            result.add(initializeAll(evalContext, o, Object.class));
        }
        return result;
    }
}
