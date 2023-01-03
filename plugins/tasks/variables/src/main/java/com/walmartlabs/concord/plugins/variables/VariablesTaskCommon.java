package com.walmartlabs.concord.plugins.variables;

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

import javax.el.*;
import java.beans.FeatureDescriptor;
import java.util.*;

public class VariablesTaskCommon {

    public interface Variables {

        Object get(String name);

        void set(String name, Object value);

        Object interpolate(Object v);
    }

    public static class EvalVariable {

        private final String name;
        private final Object value;
        private final Class<?> clazz;

        public EvalVariable(String name, Object value, Class<?> clazz) {
            this.name = name;
            this.value = value;
            this.clazz = clazz;
        }

        public String name() {
            return name;
        }

        public Object value() {
            return value;
        }

        public Class<?> clazz() {
            return clazz;
        }
    }

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

    private final Variables variables;
    private final List<EvalVariable> evalVariables;

    public VariablesTaskCommon(Variables variables, List<EvalVariable> evalVariables) {
        this.variables = variables;
        this.evalVariables = evalVariables;
    }

    public Object get(String key, Object defaultValue) {
        Object v;
        if (isNestedVariable(key)) {
            StandardELContext sc = createELContext();
            ValueExpression x = expressionFactory.createValueExpression(sc, "${" + key + "}", Object.class);
            try {
                v = x.getValue(sc);
            } catch (PropertyNotFoundException e) {
                v = null;
            }
        } else {
            v = variables.get(key);
        }
        return v != null ? v : defaultValue;
    }

    public void set(String targetKey, String sourceKey, String defaultKey) {
        Object v = variables.get(sourceKey);
        if (v == null) {
            v = variables.get(defaultKey);
        }
        variables.set(targetKey, v);
    }

    @SuppressWarnings("unchecked")
    public void set(Map<String, Object> vars) {
        vars.forEach((k, value) -> {
            Map<String, Object> vv = (Map<String, Object>) variables.interpolate(Collections.singletonMap(k, value));
            Object v = vv.get(k);
            if (isNestedVariable(k)) {
                StandardELContext sc = createELContext();
                ValueExpression x = expressionFactory.createValueExpression(sc, "${" + k + "}", Object.class);
                x.setValue(sc, v);
            } else {
                variables.set(k, v);
            }
        });
    }

    public static List<Object> concat(Collection<Object> a, Collection<Object> b) {
        if (a == null) {
            a = Collections.emptyList();
        }

        if (b == null) {
            b = Collections.emptyList();
        }

        List<Object> l = new ArrayList<>(a.size() + b.size());
        l.addAll(a);
        l.addAll(b);
        return l;
    }

    private StandardELContext createELContext() {
        ELResolver r = new VariablesResolver(variables);

        StandardELContext sc = new StandardELContext(expressionFactory);
        sc.putContext(ExpressionFactory.class, expressionFactory);
        sc.addELResolver(r);

        VariableMapper vm = sc.getVariableMapper();
        for (EvalVariable v : evalVariables) {
            vm.setVariable(v.name(), expressionFactory.createValueExpression(v.value(), v.clazz()));
        }
        return sc;
    }

    private static boolean isNestedVariable(String str) {
        return str.contains(".");
    }

    private static class VariablesResolver extends ELResolver {

        private final Variables variables;

        public VariablesResolver(Variables variables) {
            this.variables = variables;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return Object.class;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && property instanceof String) {
                String k = (String) property;
                Object v = variables.get(k);
                if (v != null) {
                    context.setPropertyResolved(true);
                    return v;
                }
            }

            return null;
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return true;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }
    }
}
