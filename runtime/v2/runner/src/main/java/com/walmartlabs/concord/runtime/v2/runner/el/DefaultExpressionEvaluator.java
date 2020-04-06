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
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;

import javax.el.*;
import javax.inject.Inject;
import java.util.*;

public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    // TODO deprecate "execution"? what about scripts - can't use "context" there?
    private static final String[] CONTEXT_VARIABLE_NAMES = {Constants.Context.CONTEXT_KEY, "execution"};

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final TaskProviders taskProviders;

    @Inject
    public DefaultExpressionEvaluator(TaskProviders taskProviders) {
        this.taskProviders = taskProviders;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(Context ctx, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) value;
            if (m.isEmpty()) {
                return expectedType.cast(m);
            }

            // use LinkedHashMap to preserve the order of keys
            Map<Object, Object> mm = evalMap(ctx, m);

            return expectedType.cast(mm);
        } else if (value instanceof List) {
            List<Object> src = (List<Object>) value;
            if (src.isEmpty()) {
                return expectedType.cast(src);
            }

            List<Object> dst = new ArrayList<>(src.size());
            for (Object vv : src) {
                dst.add(evalValue(ctx, vv, Object.class));
            }

            return expectedType.cast(dst);
        } else if (value instanceof Set) {
            Set<Object> src = (Set<Object>) value;
            if (src.isEmpty()) {
                return expectedType.cast(src);
            }

            // use LinkedHashSet to preserve the order of keys
            Set<Object> dst = new LinkedHashSet<>(src.size());
            for (Object vv : src) {
                dst.add(evalValue(ctx, vv, Object.class));
            }

            return expectedType.cast(dst);
        } else if (value.getClass().isArray()) {
            Object[] src = (Object[]) value;
            if (src.length == 0) {
                return expectedType.cast(src);
            }

            for (int i = 0; i < src.length; i++) {
                src[i] = evalValue(ctx, src[i], Object.class);
            }

            return expectedType.cast(src);
        }

        return evalValue(ctx, value, expectedType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> evalAsMap(Context ctx, Object value) {
        return eval(ctx, value, Map.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> evalAsList(Context ctx, Object value) {
        return eval(ctx, value, List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Object, Object> evalMap(Context ctx, Map<Object, Object> v) {
        Map<String, Object> result = new LinkedHashMap<>(v.size());
        Context iCtx = new IntermediateGlobalsContext(ctx, new GlobalVariablesWithOverrides(ctx.globalVariables(), result));

        for (Map.Entry<?, ?> e : v.entrySet()) {
            String kk = evalValue(iCtx, e.getKey(), String.class);

            Object vv = evalValue(iCtx, e.getValue(), Object.class);

            result.put(kk, vv);
        }

        return (Map)result;
    }

    private <T> T evalValue(Context ctx, Object value, Class<T> expectedType) {
        if (value instanceof String) {
            String s = (String) value;
            if (hasExpression(s)) {
                return evalExpr(ctx, s, expectedType);
            }
        }
        return expectedType.cast(value);
    }

    private <T> T evalExpr(Context ctx, String expr, Class<T> type) {
        ELResolver r = createResolver(ctx);

        StandardELContext sc = new StandardELContext(expressionFactory);
        sc.putContext(ExpressionFactory.class, expressionFactory);
        sc.addELResolver(r);

        // save the context as a variable
        VariableMapper vm = sc.getVariableMapper();
        for (String k : CONTEXT_VARIABLE_NAMES) {
            vm.setVariable(k, expressionFactory.createValueExpression(ctx, Context.class));
        }

        ValueExpression x = expressionFactory.createValueExpression(sc, expr, type);
        try {
            Object v = x.getValue(sc);
            return type.cast(v);
        } catch (PropertyNotFoundException e) {
            throw new RuntimeException("Can't find a variable in '" + expr + "'. Check if it is defined in the current scope. Details: " + e.getMessage());
        }
    }

    private ELResolver createResolver(Context ctx) {
        CompositeELResolver composite = new CompositeELResolver();
        composite.add(new InjectVariableResolver());
        composite.add(new GlobalVariableResolver(ctx.globalVariables()));

        composite.add(new TaskResolver(taskProviders));

        return composite;
    }

    private static boolean hasExpression(String s) {
        return s.contains("${");
    }
}
