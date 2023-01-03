package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    private final LazyExpressionEvaluator delegate;

    @Inject
    public DefaultExpressionEvaluator(TaskProviders taskProviders) {
        this.delegate = new LazyExpressionEvaluator(taskProviders);
    }

    @Override
    public <T> T eval(EvalContext ctx, Object value, Class<T> expectedType) {
        Object result = delegate.eval(ctx, value, Object.class);
        return initializeAll(result, expectedType);
    }

    private static <T> T initializeAll(Object value, Class<T> expectedType) {
        if (value instanceof LazyEvalMap) {
            LazyEvalMap m = (LazyEvalMap) value;
            return expectedType.cast(initializeMap(m));
        } else if (value instanceof LazyEvalList) {
            LazyEvalList l = (LazyEvalList) value;
            return expectedType.cast(initializeList(l));
        }
        return expectedType.cast(value);
    }

    private static Map<Object, Object> initializeMap(Map<String, Object> value) {
        Map<Object, Object> result = new LinkedHashMap<>(value.size());
        for (Map.Entry<String, Object> e : value.entrySet()) {
            Object kk = e.getKey();
            kk = initializeAll(kk, Object.class);

            Object vv = e.getValue();
            vv = initializeAll(vv, Object.class);

            result.put(kk, vv);
        }
        return result;
    }

    private static List<Object> initializeList(List<Object> value) {
        List<Object> result = new ArrayList<>();
        for (Object o : value) {
            result.add(initializeAll(o, Object.class));
        }
        return result;
    }
}
