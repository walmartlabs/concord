package com.walmartlabs.concord.runtime.v25.runner.expression;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import com.walmartlabs.concord.runtime.v25.model.Definition25;

import javax.el.ArrayELResolver;
import javax.el.BeanELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.el.StaticFieldELResolver;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExpressionService {

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final ConcurrentHashMap<String, ValueExpression> expressions = new ConcurrentHashMap<>();

    private SensitiveDataHolder sensitiveData;
    private final TaskMethods taskMethods;
    private final Map<String, Method> functions;

    public ExpressionService() {
        this(TaskMethods.NONE, List.of());
    }

    public ExpressionService(TaskMethods taskMethods) {
        this(taskMethods, List.of());
    }

    public ExpressionService(TaskMethods taskMethods, Map<String, Method> sdkFunctions) {
        this(taskMethods, sdkFunctions != null ? sdkFunctions.values() : List.of());
    }

    public ExpressionService(TaskMethods taskMethods, Iterable<Method> sdkFunctions) {
        this.taskMethods = Objects.requireNonNull(taskMethods, "taskMethods");
        this.functions = functions(sdkFunctions);
    }

    public void bindSensitiveData(SensitiveDataHolder holder) {
        if (sensitiveData != null && sensitiveData != holder) {
            throw new IllegalStateException("Expression service is already bound to another sensitive data holder");
        }
        sensitiveData = holder;
    }

    public SensitiveDataHolder sensitiveData() {
        return sensitiveData;
    }

    public void compile(Object value) {
        if (value instanceof String text && text.contains("${")) {
            expressions.computeIfAbsent(text, this::parse);
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                compile(key);
                compile(item);
            });
        } else if (value instanceof Iterable<?> values) {
            values.forEach(this::compile);
        } else if (value != null && value.getClass().isArray()) {
            for (var i = 0; i < Array.getLength(value); i++) {
                compile(Array.get(value, i));
            }
        }
    }

    public Object evaluate(Object value, Scope scope) {
        if (value instanceof String text) {
            if (!text.contains("${")) {
                return text;
            }
            var previous = Functions.enter(this, scope, sensitiveData);
            try {
                return expressions.computeIfAbsent(text, this::parse)
                        .getValue(new Context(expressionFactory, scope, taskMethods, functions));
            } catch (RuntimeException e) {
                rethrowTaskSuspension(e);
                throw e;
            } finally {
                Functions.leave(previous);
            }
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>(map.size());
            map.forEach((key, item) -> {
                var evaluatedKey = evaluate(key, scope);
                if (evaluatedKey == null) {
                    throw new ExpressionException("Expression produced a null map key");
                }
                result.put(evaluatedKey, evaluate(item, scope));
            });
            return result;
        }
        if (value instanceof Set<?> set) {
            var result = new LinkedHashSet<Object>(set.size());
            set.forEach(item -> result.add(evaluate(item, scope)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<Object>(collection.size());
            collection.forEach(item -> result.add(evaluate(item, scope)));
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            var result = new Object[Array.getLength(value)];
            for (var i = 0; i < result.length; i++) {
                result[i] = evaluate(Array.get(value, i), scope);
            }
            return result;
        }
        return value;
    }

    public <T> T evaluate(Object value, Scope scope, Class<T> expectedType) {
        try {
            return expectedType.cast(expressionFactory.coerceToType(evaluate(value, scope), expectedType));
        } catch (RuntimeException e) {
            rethrowTaskSuspension(e);
            throw new ExpressionException("Expression result cannot be converted to " + expectedType.getName(), e);
        }
    }

    public Object coerce(Object value, Class<?> expectedType) {
        try {
            return expressionFactory.coerceToType(value, expectedType);
        } catch (RuntimeException e) {
            rethrowTaskSuspension(e);
            throw new ExpressionException("Value cannot be converted to " + expectedType.getName(), e);
        }
    }

    private static void rethrowTaskSuspension(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TaskRuntime.TaskSuspensionException suspension) {
                throw suspension;
            }
            var cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
    }

    int compiledExpressionCount() {
        return expressions.size();
    }

    private static Map<String, Method> functions(Iterable<Method> sdkFunctions) {
        var result = new LinkedHashMap<>(Functions.builtInMethods());
        var methods = new ArrayList<Method>();
        if (sdkFunctions != null) {
            sdkFunctions.forEach(methods::add);
        }
        methods.sort(Comparator.comparing(ExpressionService::methodDescription,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        var sdk = new ArrayList<SdkFunction>(methods.size());
        for (var method : methods) {
            if (method == null) {
                throw new IllegalArgumentException("SDK expression function must not be null");
            }
            var annotation = method.getAnnotation(ELFunction.class);
            if (annotation == null) {
                throw new IllegalArgumentException("SDK expression function must be annotated with @ELFunction: "
                        + methodDescription(method));
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("@ELFunction method must be static: " + methodDescription(method));
            }
            if (!Modifier.isPublic(method.getModifiers())) {
                throw new IllegalArgumentException("@ELFunction method must be public: " + methodDescription(method));
            }
            var name = annotation.value().isBlank() ? method.getName() : annotation.value();
            sdk.add(new SdkFunction(name, method));
        }
        sdk.sort(Comparator.comparing(SdkFunction::name).thenComparing(function -> methodDescription(function.method())));
        for (var function : sdk) {
            var previous = result.putIfAbsent(function.name(), function.method());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate @ELFunction name '" + function.name() + "': "
                        + methodDescription(previous) + " and " + methodDescription(function.method()));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String methodDescription(Method method) {
        return method == null ? null : method.getDeclaringClass().getName() + "." + method.getName();
    }

    private record SdkFunction(String name, Method method) {
    }

    private ValueExpression parse(String source) {
        try {
            return expressionFactory.createValueExpression(new Context(expressionFactory, null, taskMethods, functions),
                    source, Object.class);
        } catch (RuntimeException e) {
            throw new ExpressionException("Invalid expression", e);
        }
    }

    private static final class Context extends ELContext {

        private final CompositeELResolver resolver = new CompositeELResolver();
        private final FunctionMapper functionMapper;
        private final VariableMapper variableMapper = new EmptyVariables();

        private Context(ExpressionFactory expressionFactory, Scope scope, TaskMethods taskMethods,
                        Map<String, Method> functions) {
            functionMapper = new Functions(functions);
            putContext(MapPathTracker.class, new MapPathTracker());
            resolver.add(new ScopeResolver(scope, taskMethods));
            resolver.add(new TaskResolver(scope, taskMethods));
            resolver.add(expressionFactory.getStreamELResolver());
            resolver.add(new StaticFieldELResolver());
            resolver.add(new ScopeMapResolver(scope));
            resolver.add(new MapELResolver());
            resolver.add(new ListELResolver());
            resolver.add(new ArrayELResolver());
            resolver.add(new BeanELResolver());
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functionMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variableMapper;
        }
    }

    private static final class ScopeResolver extends ELResolver {

        private final Scope scope;
        private final TaskMethods taskMethods;

        private ScopeResolver(Scope scope, TaskMethods taskMethods) {
            this.scope = scope;
            this.taskMethods = taskMethods;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base != null || scope == null || property == null) {
                return null;
            }
            var value = scope.lookup(property.toString());
            if (value.present()) {
                if (value.value() instanceof Map<?, ?> map) {
                    mapPaths(context).remember(map, new MapPath(property.toString(), List.of()));
                }
                context.setPropertyResolved(true);
                return value.value();
            }
            var name = property.toString();
            if (taskMethods.hasTask(name)) {
                context.setPropertyResolved(true);
                return new TaskReference(name);
            }
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == null && scope != null && property != null && scope.contains(property.toString())) {
                context.setPropertyResolved(true);
                var value = scope.get(property.toString());
                return value != null ? value.getClass() : Object.class;
            }
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (base == null && scope != null && property != null) {
                scope.set(property.toString(), value);
                context.setPropertyResolved(true);
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base == null && scope != null && property != null && scope.contains(property.toString())) {
                context.setPropertyResolved(true);
            }
            return false;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return Collections.emptyIterator();
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return base == null ? String.class : null;
        }
    }

    private static final class ScopeMapResolver extends ELResolver {

        private final Scope scope;

        private ScopeMapResolver(Scope scope) {
            this.scope = scope;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
            if (!(base instanceof Map<?, ?>) || property == null || scope == null) {
                return;
            }
            var path = mapPaths(context).path(base);
            if (path == null) {
                return;
            }
            var keys = new ArrayList<>(path.keys());
            keys.add(property.toString());
            var segments = new ArrayList<String>(keys.size() + 1);
            segments.add(path.root());
            segments.addAll(keys);
            scope.set(segments, value);
            context.setPropertyResolved(true);
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (!(base instanceof Map<?, ?> map) || property == null) {
                return null;
            }
            var path = mapPaths(context).path(base);
            var nested = map.get(property);
            if (path != null && nested instanceof Map<?, ?> nestedMap) {
                var keys = new ArrayList<>(path.keys());
                keys.add(property.toString());
                mapPaths(context).remember(nestedMap, new MapPath(path.root(), List.copyOf(keys)));
            }
            return null;
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return false;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return Collections.emptyIterator();
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Map.class;
        }
    }

    private static MapPathTracker mapPaths(ELContext context) {
        return (MapPathTracker) context.getContext(MapPathTracker.class);
    }

    private record MapPath(String root, List<String> keys) {
    }

    private static final class MapPathTracker {

        private final Map<Object, MapPath> paths = new IdentityHashMap<>();

        private void remember(Object map, MapPath path) {
            paths.put(map, path);
        }

        private MapPath path(Object map) {
            return paths.get(map);
        }
    }

    private static final class TaskResolver extends ELResolver {

        private final Scope scope;
        private final TaskMethods taskMethods;

        private TaskResolver(Scope scope, TaskMethods taskMethods) {
            this.scope = scope;
            this.taskMethods = taskMethods;
        }

        @Override
        public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes,
                             Object[] params) {
            if (base instanceof TaskReference task && method != null) {
                context.setPropertyResolved(true);
                return taskMethods.invoke(task.name(), method.toString(), params != null ? params : new Object[0],
                        scope);
            }
            var invocation = taskMethods.resolveBean(base, method != null ? method.toString() : null,
                    params != null ? params : new Object[0], scope);
            if (invocation != null) {
                context.setPropertyResolved(true);
                return invocation.value();
            }
            return null;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return true;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return Collections.emptyIterator();
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return base instanceof TaskReference ? String.class : null;
        }
    }

    private record TaskReference(String name) {
    }

    private static final class EmptyVariables extends VariableMapper {

        @Override
        public ValueExpression resolveVariable(String variable) {
            return null;
        }

        @Override
        public ValueExpression setVariable(String variable, ValueExpression expression) {
            return null;
        }
    }
    public static final class Functions extends FunctionMapper {

        private static final ThreadLocal<ExpressionService> EXPRESSION_SERVICE = new ThreadLocal<>();
        private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
        private static final ThreadLocal<SensitiveDataHolder> SENSITIVE_DATA = new ThreadLocal<>();

        private final Map<String, Method> methods;

        private Functions(Map<String, Method> methods) {
            this.methods = methods;
        }

        private static Evaluation enter(ExpressionService expressionService, Scope scope,
                                        SensitiveDataHolder sensitiveData) {
            var previous = new Evaluation(EXPRESSION_SERVICE.get(), CURRENT.get(), SENSITIVE_DATA.get());
            EXPRESSION_SERVICE.set(expressionService);
            CURRENT.set(scope);
            SENSITIVE_DATA.set(sensitiveData);
            return previous;
        }

        private static void leave(Evaluation previous) {
            if (previous.expressionService() == null) {
                EXPRESSION_SERVICE.remove();
            } else {
                EXPRESSION_SERVICE.set(previous.expressionService());
            }
            if (previous.scope() == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous.scope());
            }
            if (previous.sensitiveData() == null) {
                SENSITIVE_DATA.remove();
            } else {
                SENSITIVE_DATA.set(previous.sensitiveData());
            }
        }

        private record Evaluation(ExpressionService expressionService, Scope scope, SensitiveDataHolder sensitiveData) {
        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            var name = prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
            return methods.get(name);
        }

        public static Map<String, Object> allVariables() {
            return scope().snapshot();
        }

        public static boolean hasVariable(String path) {
            return resolve(path).present();
        }

        public static boolean hasNonNullVariable(String path) {
            var value = resolve(path);
            return value.present() && value.value() != null;
        }

        public static Object orDefault(String path, Object fallback) {
            var value = resolve(path);
            return value.present() && value.value() != null ? value.value() : fallback;
        }

        public static String currentFlowName() {
            return scope().flowName();
        }

        public static boolean hasFlow(String name) {
            return scope().hasFlow(name);
        }

        public static String sensitive(Object value) {
            if (value == null) {
                return null;
            }
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Only string values can be masked. Got a "
                        + value.getClass() + " instead");
            }
            if (!text.isBlank()) {
                var holder = SENSITIVE_DATA.get();
                if (holder == null) {
                    throw new IllegalStateException("Sensitive data holder is not configured");
                }
                holder.add(text);
            }
            return text;
        }

        public static boolean isDryRun() {
            return scope().dryRun();
        }

        public static boolean isDebug() {
            return scope().debug();
        }

        public static String uuid() {
            return UUID.randomUUID().toString();
        }

        @SuppressWarnings("unchecked")
        public static Map<String, Object> evalAsMap(Object value) {
            if (!(value instanceof Map<?, ?>)) {
                throw new ExpressionException("Expected a mapping");
            }
            var evaluated = expressionService().evaluate(value, scope());
            var result = new LinkedHashMap<String, Object>();
            ((Map<?, ?>) evaluated).forEach((key, item) -> {
                var name = String.valueOf(key);
                var existing = scope().lookup(name).value();
                var merged = item;
                if (existing instanceof Map<?, ?> existingMap && item instanceof Map<?, ?> itemMap) {
                    merged = Definition25.deepMerge((Map<String, Object>) existingMap, (Map<String, Object>) itemMap);
                }
                if (merged != null) {
                    result.put(name, merged);
                }
            });
            return result;
        }

        public static Object throwError(String message) {
            throw new UserDefinedException(message);
        }

        @SuppressWarnings("unchecked")
        private static Scope.Lookup resolve(String path) {
            var parts = path.split("\\.");
            var result = scope().lookup(parts[0]);
            if (!result.present()) {
                return result;
            }
            var value = result.value();
            for (var i = 1; i < parts.length; i++) {
                if (value instanceof Map<?, ?> map && map.containsKey(parts[i])) {
                    value = ((Map<String, Object>) map).get(parts[i]);
                } else if (value instanceof List<?> list) {
                    try {
                        value = list.get(Integer.parseInt(parts[i]));
                    } catch (RuntimeException e) {
                        return new Scope.Lookup(false, null);
                    }
                } else {
                    return new Scope.Lookup(false, null);
                }
            }
            return new Scope.Lookup(true, value);
        }

        private static Scope scope() {
            var result = CURRENT.get();
            if (result == null) {
                throw new IllegalStateException("No expression scope is active");
            }
            return result;
        }

        private static ExpressionService expressionService() {
            var result = EXPRESSION_SERVICE.get();
            if (result == null) {
                throw new IllegalStateException("No expression service is active");
            }
            return result;
        }

        private static Map<String, Method> builtInMethods() {
            try {
                var result = new LinkedHashMap<String, Method>();
                result.put("allVariables", Functions.class.getMethod("allVariables"));
                result.put("hasVariable", Functions.class.getMethod("hasVariable", String.class));
                result.put("hasNonNullVariable", Functions.class.getMethod("hasNonNullVariable", String.class));
                result.put("orDefault", Functions.class.getMethod("orDefault", String.class, Object.class));
                result.put("currentFlowName", Functions.class.getMethod("currentFlowName"));
                result.put("hasFlow", Functions.class.getMethod("hasFlow", String.class));
                result.put("sensitive", Functions.class.getMethod("sensitive", Object.class));
                result.put("isDryRun", Functions.class.getMethod("isDryRun"));
                result.put("isDebug", Functions.class.getMethod("isDebug"));
                result.put("uuid", Functions.class.getMethod("uuid"));
                result.put("evalAsMap", Functions.class.getMethod("evalAsMap", Object.class));
                result.put("throw", Functions.class.getMethod("throwError", String.class));
                return Collections.unmodifiableMap(result);
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    public interface TaskMethods {

        TaskMethods NONE = new TaskMethods() {
            @Override
            public boolean hasTask(String name) {
                return false;
            }

            @Override
            public Object invoke(String taskName, String methodName, Object[] arguments, Scope scope) {
                throw new UnsupportedOperationException("Task method expressions are not configured");
            }
        };

        boolean hasTask(String name);

        Object invoke(String taskName, String methodName, Object[] arguments, Scope scope);

        default BeanInvocation resolveBean(Object base, String methodName, Object[] arguments, Scope scope) {
            return null;
        }

        record BeanInvocation(Object value) {
        }
    }

    public static final class ExpressionException extends RuntimeException {

        public ExpressionException(String message) {
            super(message);
        }

        public ExpressionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
