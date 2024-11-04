package com.walmartlabs.concord.plugins.variables.v2;

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

import com.walmartlabs.concord.plugins.variables.VariablesTaskCommon;
import com.walmartlabs.concord.plugins.variables.VariablesTaskCommon.Variables;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Named("vars")
@DryRunReady
@SuppressWarnings("unused")
public class VariablesTaskV2 implements Task {

    private final Context context;
    private final VariablesTaskCommon delegate;

    @Inject
    public VariablesTaskV2(Context context) {
        this.context = context;
        this.delegate = new VariablesTaskCommon(new VariablesAdapter(context), Collections.singletonList(new VariablesTaskCommon.EvalVariable(Constants.Context.CONTEXT_KEY, context, Context.class)));
    }

    public Object get(String key, Object defaultValue) {
        return delegate.get(key, defaultValue);
    }

    public void set(String targetKey, String sourceKey, String defaultKey) {
       delegate.set(targetKey, sourceKey, defaultKey);
    }

    public void set(Map<String, Object> vars) {
        delegate.set(vars);
    }

    public Object eval(Object v) {
        return context.eval(v, Object.class);
    }

    public List<Object> concat(Collection<Object> a, Collection<Object> b) {
        return VariablesTaskCommon.concat(a, b);
    }

    private static class VariablesAdapter implements Variables {

        private final Context context;

        private VariablesAdapter(Context context) {
            this.context = context;
        }

        @Override
        public Object get(String name) {
            return context.variables().get(name);
        }

        @Override
        public void set(String name, Object value) {
            context.variables().set(name, value);
        }

        @Override
        public Object interpolate(Object v) {
            return context.eval(v, Object.class);
        }
    }
}
