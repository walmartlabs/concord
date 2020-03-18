package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.InjectVariable;
import org.eclipse.sisu.Typed;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

@Named
@Typed
public class DefaultTaskProvider implements TaskProvider {

    private final Injector injector;
    private final TaskHolder<com.walmartlabs.concord.sdk.Task> v1Holder;
    private final TaskHolder<Task> v2Holder;
    private final DefaultVariableInjector defaultVariableInjector;

    @Inject
    public DefaultTaskProvider(Injector injector,
                               @TaskHolder.V1 TaskHolder<com.walmartlabs.concord.sdk.Task> v1Holder,
                               @TaskHolder.V2 TaskHolder<Task> v2Holder,
                               DefaultVariableInjector defaultVariableInjector) {

        this.v1Holder = v1Holder;
        this.v2Holder = v2Holder;
        this.injector = injector;
        this.defaultVariableInjector = defaultVariableInjector;
    }

    @Override
    public Task createTask(Context ctx, String key) {
        Class<? extends Task> klass = v2Holder.get(key);
        if (klass != null) {
            return defaultVariableInjector.inject(key, injector.getInstance(klass));
        }

        Class<? extends com.walmartlabs.concord.sdk.Task> klassV1 = v1Holder.get(key);
        if (klassV1 != null) {
            com.walmartlabs.concord.sdk.Task v1Task = injector.getInstance(klassV1);

            return new Task() {

                @Override
                public Serializable execute(TaskContext ctx) throws Exception {
                    Map<String, Serializable> result = new HashMap<>();
                    ContextWrapper v1Context = new ContextWrapper(ctx, result);

                    Map<String, Object> allVars = collectAllVariables(ctx);
                    allVars.put(Constants.Context.CONTEXT_KEY, v1Context);

                    injectVariables(v1Task, allVars);

                    v1Task.execute(v1Context);

                    return (Serializable) result;
                }
            };
        }

        return null;
    }

    private static Map<String, Object> collectAllVariables(TaskContext ctx) {
        Map<String, Object> result = new HashMap<>();
        result.putAll(ctx.globalVariables().toMap());
        result.putAll(ctx.input());
        return result;
    }

    private static void injectVariables(Object o, Map<String, Object> variables) {
        Class<?> clazz = o.getClass();
        boolean isSingleton = clazz.isAnnotationPresent(Singleton.class);
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                String v = getAnnotationValue(f);
                if (v == null) {
                    continue;
                }

                inject(f, v, o, variables, isSingleton);
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static String getAnnotationValue(Field f) {
        InjectVariable iv = f.getAnnotation(InjectVariable.class);
        if (iv != null) {
            return iv.value();
        }
        return null;
    }

    private static void inject(Field f, String value, Object base, Map<String, Object> variables, boolean isSingleton) {
        if (isSingleton) {
            return;
        }

        try {
            Object variableValue = variables.get(value);
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            f.set(base, variableValue);

        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error while setting property '" + f.getName() + "': " + e.getMessage(), e);
        }
    }

    public static class ContextWrapper implements com.walmartlabs.concord.sdk.Context {

        private final TaskContext ctx;
        private final Map<String, Object> allVariables = new HashMap<>();
        private final Map<String, Serializable> result;

        public ContextWrapper(TaskContext ctx, Map<String, Serializable> result) {
            this.ctx = ctx;
            this.result = result;

            this.allVariables.putAll(collectAllVariables(ctx));
        }

        @Override
        public Object getVariable(String key) {
            return allVariables.get(key);
        }

        @Override
        public void setVariable(String key, Object value) {
            if (value instanceof Serializable) {
                result.put(key, (Serializable) value);
            }

            allVariables.put(key, value);
        }

        @Override
        public void removeVariable(String key) {
            result.remove(key);
            allVariables.remove(key);
        }

        @Override
        public void setProtectedVariable(String key, Object value) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Object getProtectedVariable(String key) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Set<String> getVariableNames() {
            return allVariables.keySet();
        }

        @Override
        public UUID getEventCorrelationId() {
            return null;
        }

        @Override
        public <T> T eval(String expr, Class<T> type) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Object interpolate(Object v) {
            return ctx.interpolate(v, Object.class);
        }

        @Override
        public Object interpolate(Object v, Map<String, Object> variables) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Map<String, Object> toMap() {
            return Collections.unmodifiableMap(allVariables);
        }

        @Override
        public void suspend(String eventName) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void suspend(String eventName, Object payload, boolean resumeFromSameStep) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void form(String formName, Map<String, Object> formOptions) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getProcessDefinitionId() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getElementId() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getCurrentFlowName() {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}
