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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.ExceptionUtils;
import com.walmartlabs.concord.runtime.v2.runner.el.functions.*;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.MapELResolver;
import com.walmartlabs.concord.runtime.v2.runner.el.resolvers.*;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vm.WrappedException;
import com.walmartlabs.concord.runtime.v2.sdk.*;

import javax.el.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.ConfigurationUtils.*;
import static com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext.withEvalContext;

/**
 * Evaluates values. Allows partial evaluation of nested data.
 */
public class LazyExpressionEvaluator implements ExpressionEvaluator {

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final TaskProviders taskProviders;
    private final FunctionMapper functionMapper;
    private final List<CustomTaskMethodResolver> taskMethodResolvers;
    private final List<CustomBeanMethodResolver> beanMethodResolvers;

    public LazyExpressionEvaluator(TaskProviders taskProviders,
                                   List<CustomTaskMethodResolver> taskMethodResolvers,
                                   List<CustomBeanMethodResolver> beanMethodResolvers) {
        this.taskProviders = taskProviders;
        this.functionMapper = createFunctionMapper();
        this.taskMethodResolvers = taskMethodResolvers;
        this.beanMethodResolvers = beanMethodResolvers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(EvalContext ctx, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            var m = nestedToMap((Map<String, Object>) value);
            value = mergeWithVariables(ctx, m, ((Map<String, Object>) value).keySet().stream().filter(ConfigurationUtils::isNestedKey).collect(Collectors.toSet()));
        }

        if (ctx.useIntermediateResults() && value instanceof Map) {
            var m = (Map<String, Object>) value;
            if (m.isEmpty()) {
                return expectedType.cast(m);
            }

            return expectedType.cast(new LazyEvalMap(this, m, ctx));
        }

        return evalValue(LazyEvalContext.of(ctx, null), value, expectedType);
    }

    @SuppressWarnings("unchecked")
    <T> T evalValue(LazyEvalContext ctx, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            var m = (Map<String, Object>) value;
            return expectedType.cast(new LazyEvalMap(this, ctx, m));
        } else if (value instanceof List<?> src) {
            return expectedType.cast(new LazyEvalList(this, ctx, src));
        } else if (value instanceof Set<?> src) {
            // use LinkedHashSet to preserve the order of keys
            var dst = new LinkedHashSet<>(src.size());
            for (var vv : src) {
                dst.add(evalValue(ctx, vv, Object.class));
            }

            return expectedType.cast(dst);
        } else if (value.getClass().isArray()) {
            var src = (Object[]) value;
            if (src.length == 0) {
                return expectedType.cast(src);
            }

            for (int i = 0; i < src.length; i++) {
                src[i] = evalValue(ctx, src[i], Object.class);
            }

            return expectedType.cast(src);
        } else if (value instanceof String s) {
            if (hasExpression(s)) {
                return evalExpr(ctx, s, expectedType);
            }
        }

        return expectedType.cast(value);
    }

    private <T> T evalExpr(LazyEvalContext ctx, String expr, Class<T> type) {
        var resolver = createResolver(ctx, expressionFactory);

        var sc = new StandardELContext(expressionFactory) {
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

        try {
            var x = expressionFactory.createValueExpression(sc, expr, type);
            var v = withEvalContext(ctx, () -> x.getValue(sc));
            return type.cast(v);
        } catch (PropertyNotFoundException e) {
            if (ctx.undefinedVariableAsNull()) {
                return null;
            }

            var errorMessage = propertyNameFromException(e)
                    .map(propName -> String.format("Can't find a variable %s. " +
                            "Check if it is defined in the current scope. Details: %s", propName, e.getMessage()))
                    .orElse(String.format("Can't find the specified variable. " +
                            "Check if it is defined in the current scope. Details: %s", e.getMessage()));

            throw new UserDefinedException(exceptionPrefix(expr) + errorMessage);
        } catch (MethodNotFoundException e) {
            throw new UserDefinedException(exceptionPrefix(expr) + e.getMessage());
        } catch (UserDefinedException e) {
            throw e;
        } catch (javax.el.ELException e) {
            var lastElException = ExceptionUtils.findLastException(e, javax.el.ELException.class);
            if (lastElException.getCause() instanceof UserDefinedException ue) {
                throw ue;
            } else if (e.getCause() instanceof com.sun.el.parser.ParseException pe) {
                throw new UserDefinedException("while parsing expression '" + expr + "': " + pe.getMessage());
            } else if (lastElException.getCause() instanceof Exception ee) {
                throw new WrappedException(exceptionPrefix(expr), ee);
            }
            throw lastElException;
        } catch (Exception e) {
            throw new WrappedException(exceptionPrefix(expr), e);
        }
    }

    /**
     * Based on the original code from {@link StandardELContext#getELResolver()}.
     * Creates a {@link ELResolver} instance with "sub-resolvers" in the original order.
     */
    private ELResolver createResolver(LazyEvalContext evalContext,
                                      ExpressionFactory expressionFactory) {

        var r = new CompositeELResolver();
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
        r.add(new MethodAccessorResolver());
        r.add(new ResourceBundleELResolver());
        r.add(new ListELResolver());
        r.add(new ArrayELResolver());
        if (evalContext.context() != null) {
            r.add(new TaskMethodResolver(taskMethodResolvers, evalContext.context()));
        }
        r.add(new CompositeBeanELResolver(taskMethodResolvers, beanMethodResolvers));
        return r;
    }

    private static FunctionMapper createFunctionMapper() {
        var functions = new HashMap<String, Method>();
        functions.put("hasVariable", HasVariableFunction.getMethod());
        functions.put("hasNonNullVariable", HasNonNullVariableFunction.getMethod());
        functions.put("orDefault", OrDefaultFunction.getMethod());
        functions.put("allVariables", AllVariablesFunction.getMethod());
        functions.put("currentFlowName", CurrentFlowNameFunction.getMethod());
        functions.put("evalAsMap", EvalAsMapFunction.getMethod());
        functions.put("isDebug", IsDebugFunction.getMethod());
        functions.put("throw", ThrowFunction.getMethod());
        functions.put("hasFlow", HasFlowFunction.getMethod());
        functions.put("uuid", UuidFunction.getMethod());
        functions.put("isDryRun", IsDryRunFunction.getMethod());
        return new FunctionMapper(functions);
    }

    private static boolean hasExpression(String s) {
        return s.contains("${");
    }

    private static Map<String, Object> nestedToMap(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (var e : value.entrySet()) {
            if (isNestedKey(e.getKey())) {
                var m = toNested(e.getKey(), e.getValue());
                result = deepMerge(result, m);
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeWithVariables(EvalContext ctx, Map<String, Object> m, Set<String> nestedKeys) {
        var result = new LinkedHashMap<String, Object>();
        for (var e : m.entrySet()) {
            var key = e.getKey();
            var value = e.getValue();
            var isNested = nestedKeys.stream().anyMatch(s -> s.startsWith(key + "."));
            if (isNested && ctx.variables().has(key)) {
                var o = ctx.variables().get(key);
                if (o instanceof Map && e.getValue() instanceof Map) {
                    var valuesFromVars = (Map<String, Object>) o;
                    value = deepMerge(valuesFromVars, (Map<String, Object>) value);
                }
            }
            result.put(key, value);
        }
        return result;
    }

    private static String exceptionPrefix(String expr) {
        return "while evaluating expression '" + expr + "': ";
    }

    private static final String PROP_NOT_FOUND_EL_MESSAGE = "ELResolver cannot handle a null base Object with identifier ";

    private static Optional<String> propertyNameFromException(PropertyNotFoundException e) {
        if (e.getMessage() == null) {
            return Optional.empty();
        }

        if (e.getMessage().startsWith(PROP_NOT_FOUND_EL_MESSAGE)) {
            return Optional.of(e.getMessage().substring(PROP_NOT_FOUND_EL_MESSAGE.length()));
        }

        return Optional.empty();
    }
}
