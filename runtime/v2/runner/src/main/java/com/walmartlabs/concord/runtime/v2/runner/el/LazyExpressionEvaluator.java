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

import com.walmartlabs.concord.runtime.v2.runner.el.functions.AllVariablesFunction;
import com.walmartlabs.concord.runtime.v2.runner.el.functions.HasVariableFunction;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.BeanELResolver;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.TaskResolver;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.VariableResolver;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;

import javax.el.*;
import java.lang.reflect.Method;
import java.util.*;

import static com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext.withEvalContext;

/**
 * Evaluates values. Allows partial evaluation of nested data.
 */
public class LazyExpressionEvaluator implements ExpressionEvaluator {

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final TaskProviders taskProviders;
    private final FunctionMapper functionMapper;

    public LazyExpressionEvaluator(TaskProviders taskProviders) {
        this.taskProviders = taskProviders;
        this.functionMapper = createFunctionMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(EvalContext context, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (context.useIntermediateResults() && value instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) value;
            if (m.isEmpty()) {
                return expectedType.cast(m);
            }

            return expectedType.cast(new LazyEvalMap(this, m, context));
        }

        return evalValue(LazyEvalContext.of(context, null), value, expectedType);
    }

    @SuppressWarnings("unchecked")
    public <T> T evalValue(LazyEvalContext ctx, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) value;
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

    private <T> T evalExpr(LazyEvalContext ctx, String expr, Class<T> type) {
        ELResolver resolver = createResolver(ctx, expressionFactory);

        StandardELContext sc = new StandardELContext(expressionFactory) {
            @Override
            public ELResolver getELResolver() {
                return resolver;
            }

            @Override
            public FunctionMapper getFunctionMapper() {
                return functionMapper;
            }
        };
        sc.putContext(ExpressionFactory.class, expressionFactory);

        ValueExpression x = expressionFactory.createValueExpression(sc, expr, type);
        try {
            Object v = withEvalContext(ctx, () -> x.getValue(sc));
            return type.cast(v);
        } catch (PropertyNotFoundException e) {
            if (ctx.undefinedVariableAsNull()) {
                return null;
            }
            throw new RuntimeException(String.format("Can't find the specified variable in '%s'. " +
                    "Check if it is defined in the current scope. Details: %s", expr, e.getMessage()));
        }
    }

    /**
     * Based on the original code from {@link StandardELContext#getELResolver()}.
     * Creates a {@link ELResolver} instance with "sub-resolvers" in the original order.
     */
    private ELResolver createResolver(LazyEvalContext evalContext,
                                      ExpressionFactory expressionFactory) {

        CompositeELResolver r = new CompositeELResolver();
        if (evalContext.scope() != null) {
            r.add(new VariableResolver(evalContext.scope()));
        }
        r.add(new VariableResolver(evalContext.variables()));
        if (evalContext.context() != null) {
            r.add(new TaskResolver(evalContext.context(), taskProviders));
        }
        r.add(expressionFactory.getStreamELResolver());
        r.add(new StaticFieldELResolver());
        r.add(new MapELResolver());
        r.add(new ResourceBundleELResolver());
        r.add(new ListELResolver());
        r.add(new ArrayELResolver());
        r.add(new BeanELResolver());
        return r;
    }

    private static FunctionMapper createFunctionMapper() {
        Map<String, Method> functions = new HashMap<>();
        functions.put("hasVariable", HasVariableFunction.getMethod());
        functions.put("allVariables", AllVariablesFunction.getMethod());
        return new FunctionMapper(functions);
    }

    private static boolean hasExpression(String s) {
        return s.contains("${");
    }
}
