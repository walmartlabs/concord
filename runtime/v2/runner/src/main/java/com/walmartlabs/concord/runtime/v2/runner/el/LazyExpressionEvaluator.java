package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.runner.context.IntermediateGlobalsContext;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;

import javax.el.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates values. Allows partial evaluation of nested data.
 */
public class LazyExpressionEvaluator implements ExpressionEvaluator {

    // TODO deprecate "execution"? what about scripts - can't use "context" there?
    private static final String[] CONTEXT_VARIABLE_NAMES = {Constants.Context.CONTEXT_KEY, "execution"};

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final TaskProviders taskProviders;

    public LazyExpressionEvaluator(TaskProviders taskProviders) {
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

            LazyEvalMap result = new LazyEvalMap(this, m);
            Context evalContext = new IntermediateGlobalsContext(ctx, new GlobalVariablesWithOverrides(ctx.globalVariables(), (Map) result));
            result.setContext(evalContext);
            return expectedType.cast(result);
        }

        return evalValue(ctx, value, expectedType);
    }

    @SuppressWarnings("unchecked")
    public <T> T evalValue(Context ctx, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) value;
            if (m.isEmpty()) {
                return expectedType.cast(m);
            }

            return expectedType.cast(new LazyEvalMap(this, ctx, m));
        } else if (value instanceof List) {
            List<Object> src = (List<Object>) value;
            if (src.isEmpty()) {
                return expectedType.cast(src);
            }

            return expectedType.cast(new LazyEvalList(this, ctx, src));
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
        } else if (value instanceof String) {
            String s = (String) value;
            if (hasExpression(s)) {
                return evalExpr(ctx, s, expectedType);
            }
        }

        return expectedType.cast(value);
    }

    private <T> T evalExpr(Context ctx, String expr, Class<T> type) {
        ELResolver resolver = createResolver(ctx, expressionFactory);

        StandardELContext sc = new StandardELContext(expressionFactory) {
            @Override
            public ELResolver getELResolver() {
                return resolver;
            }
        };
        sc.putContext(ExpressionFactory.class, expressionFactory);

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
            throw new RuntimeException(String.format("Can't find the specified variable in '%s'. " +
                    "Check if it is defined in the current scope. Details: %s", expr, e.getMessage()));
        }
    }

    /**
     * Based on the original code from {@link StandardELContext#getELResolver()}.
     * Creates a {@link ELResolver} instance with "sub-resolvers" in the original order.
     */
    private ELResolver createResolver(Context ctx, ExpressionFactory expressionFactory) {
        CompositeELResolver r = new CompositeELResolver();
        r.add(new InjectVariableResolver());
        r.add(new GlobalVariableResolver(ctx.globalVariables()));
        r.add(new TaskResolver(taskProviders));
        r.add(expressionFactory.getStreamELResolver());
        r.add(new StaticFieldELResolver());
        r.add(new MapELResolver());
        r.add(new ResourceBundleELResolver());
        r.add(new ListELResolver());
        r.add(new ArrayELResolver());
        r.add(new BeanELResolver());
        return r;
    }

    private static boolean hasExpression(String s) {
        return s.contains("${");
    }
}
