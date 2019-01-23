package com.walmartlabs.concord.plugins.variables;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.el.*;
import javax.inject.Named;
import java.beans.FeatureDescriptor;
import java.util.*;

@Named("vars")
public class VariablesTask implements Task {

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

    public Object get(@InjectVariable("context") Context ctx, String key, Object defaultValue) {
        Object v;
        if (isNestedVariable(key)) {
            StandardELContext sc = createELContext(ctx);
            ValueExpression x = expressionFactory.createValueExpression(sc, "${" + key + "}", Object.class);
            try {
                v = x.getValue(sc);
            } catch (PropertyNotFoundException e) {
                v = null;
            }
        } else {
            v = ctx.getVariable(key);
        }
        return v != null ? v : defaultValue;
    }

    public void set(@InjectVariable("context") Context ctx, String targetKey, String sourceKey, String defaultKey) {
        Object v = ctx.getVariable(sourceKey);
        if (v == null) {
            v = ctx.getVariable(defaultKey);
        }
        ctx.setVariable(targetKey, v);
    }

    public void set(@InjectVariable("context") Context ctx, Map<String, Object> vars) {
        vars.forEach((k, v) -> {
            if (isNestedVariable(k)) {
                StandardELContext sc = createELContext(ctx);
                ValueExpression x = expressionFactory.createValueExpression(sc, "${" + k + "}", Object.class);
                x.setValue(sc, v);
            } else {
                ctx.setVariable(k, v);
            }
        });
    }

    public Object eval(@InjectVariable("context") Context ctx, Object v) {
        return ctx.interpolate(v);
    }

    public List<Object> concat(Collection<Object> a, Collection<Object> b) {
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

    private boolean isNestedVariable(String str) {
        return str.contains(".");
    }

    private StandardELContext createELContext(Context ctx) {
        ELResolver r = new VariablesResolver(ctx);

        StandardELContext sc = new StandardELContext(expressionFactory);
        sc.putContext(ExpressionFactory.class, expressionFactory);
        sc.addELResolver(r);

        VariableMapper vm = sc.getVariableMapper();
        vm.setVariable(InternalConstants.Context.CONTEXT_KEY, expressionFactory.createValueExpression(ctx, Context.class));
        return sc;
    }

    private static class VariablesResolver extends ELResolver {

        private final Context ctx;

        public VariablesResolver(Context executionContext) {
            this.ctx = executionContext;
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
                Object v = ctx.getVariable(k);
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
