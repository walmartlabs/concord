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
    private final SensitiveDataProcessor sensitiveDataProcessor;

    @Inject
    public DefaultExpressionEvaluator(TaskProviders taskProviders,
                                      FunctionHolder functionHolder,
                                      List<CustomTaskMethodResolver> taskMethodResolvers,
                                      List<CustomBeanMethodResolver> beanMethodResolvers,
                                      SensitiveDataProcessor sensitiveDataProcessor) {
        this.delegate = new LazyExpressionEvaluator(taskProviders, functionHolder, taskMethodResolvers, beanMethodResolvers, sensitiveDataProcessor);
        this.sensitiveDataProcessor = sensitiveDataProcessor;
    }

    @Override
    public <T> T eval(EvalContext ctx, Object value, Class<T> expectedType) {
        Object result = delegate.eval(ctx, value, Object.class);
        return initializeAll(ctx, result, expectedType);
    }

    private <T> T initializeAll(EvalContext evalContext, Object value, Class<T> expectedType) {
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            return expectedType.cast(initializeMap(evalContext, m));
        } else if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            if (set.isEmpty()) {
                return expectedType.cast(new LinkedHashSet<>());
            }
            return expectedType.cast(initializeSet(evalContext, set));
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                return expectedType.cast(new ArrayList<>());
            }
            return expectedType.cast(initializeList(evalContext, collection));
        } else if (value != null && value.getClass().isArray()) {
            Object[] arr = (Object[])value;
            if (arr.length == 0) {
                return expectedType.cast(arr);
            }
            return expectedType.cast(initializeArray(evalContext, arr));
        } else if (evalContext.resolveLazyValues() && value instanceof LazyValue<?> v) {
            var resolved = v.resolve(evalContext.context());
            try {
                sensitiveDataProcessor.process(resolved, v.getClass().getMethod("resolve", Context.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("can't find 'resolve' method in " + resolved.getClass() + ". This is most likely a bug.");
            }
            return expectedType.cast(resolved);
        }
        return expectedType.cast(value);
    }

    private Object[] initializeArray(EvalContext evalContext, Object[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = initializeAll(evalContext, arr[i], Object.class);
        }
        return arr;
    }

    private Map<Object, Object> initializeMap(EvalContext evalContext, Map<?, ?> value) {
        Map<Object, Object> result = new LinkedHashMap<>(value.size());
        for (Map.Entry<?, ?> e : value.entrySet()) {
            Object kk = e.getKey();
            kk = initializeAll(evalContext, kk, Object.class);

            Object vv = e.getValue();
            vv = initializeAll(evalContext, vv, Object.class);

            result.put(kk, vv);
        }
        return result;
    }

    private List<Object> initializeList(EvalContext evalContext, Collection<?> value) {
        List<Object> result = new ArrayList<>(value.size());
        for (Object o : value) {
            result.add(initializeAll(evalContext, o, Object.class));
        }
        return result;
    }

    private Set<Object> initializeSet(EvalContext evalContext, Collection<?> value) {
        Set<Object> result = new LinkedHashSet<>(value.size());
        for (Object o : value) {
            result.add(initializeAll(evalContext, o, Object.class));
        }
        return result;
    }
}
