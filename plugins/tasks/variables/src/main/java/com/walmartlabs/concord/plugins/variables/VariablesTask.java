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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.plugins.variables.VariablesTaskCommon.EvalVariable;

@Named("vars")
@SuppressWarnings("unused")
public class VariablesTask implements Task {

    public Object get(@InjectVariable("context") Context ctx, String key, Object defaultValue) {
        return delegate(ctx).get(key, defaultValue);
    }

    public void set(@InjectVariable("context") Context ctx, String targetKey, String sourceKey, String defaultKey) {
        delegate(ctx).set(targetKey, sourceKey, defaultKey);
    }

    public void set(@InjectVariable("context") Context ctx, Map<String, Object> vars) {
        delegate(ctx).set(vars);
    }

    public Object eval(@InjectVariable("context") Context ctx, Object v) {
        return ctx.interpolate(v);
    }

    public List<Object> concat(Collection<Object> a, Collection<Object> b) {
        return VariablesTaskCommon.concat(a, b);
    }

    private static VariablesTaskCommon delegate(Context ctx) {
        return new VariablesTaskCommon(new VariablesAdapter(ctx), Collections.singletonList(new EvalVariable(Constants.Context.CONTEXT_KEY, ctx, Context.class)));
    }

    private static class VariablesAdapter implements VariablesTaskCommon.Variables {

        private final Context context;

        private VariablesAdapter(Context context) {
            this.context = context;
        }

        @Override
        public Object get(String name) {
            return context.getVariable(name);
        }

        @Override
        public void set(String name, Object value) {
            context.setVariable(name, value);
        }

        @Override
        public Object interpolate(Object v) {
            return context.interpolate(v);
        }
    }
}
