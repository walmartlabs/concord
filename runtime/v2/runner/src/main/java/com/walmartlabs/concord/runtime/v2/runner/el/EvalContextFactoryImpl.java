package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.context.ContextVariables;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextVariablesWithOverrides;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;

import java.util.Map;

public class EvalContextFactoryImpl implements EvalContextFactory {

    @Override
    public EvalContext scope(Context ctx) {
        return EvalContext.builder()
                .context(ctx)
                .variables(new ContextVariables(ctx))
                .useIntermediateResults(true)
                .build();
    }

    @Override
    public EvalContext global(Context ctx) {
        return EvalContext.builder()
                .context(ctx)
                .variables(new ContextVariables(ctx))
                .build();
    }

    @Override
    public EvalContext global(Context ctx, boolean resolveLazyValues) {
        return EvalContext.builder()
                .context(ctx)
                .variables(new ContextVariables(ctx))
                .resolveLazyValues(resolveLazyValues)
                .build();
    }

    @Override
    public EvalContext global(Context ctx, Map<String, Object> additionalVariables) {
        return EvalContext.builder()
                .context(ctx)
                .variables(new ContextVariablesWithOverrides(ctx, additionalVariables))
                .build();
    }

    @Override
    public EvalContext strict(Context ctx, Map<String, Object> variables) {
        return EvalContext.builder()
                .context(ctx)
                .variables(new MapBackedVariables(variables))
                .build();
    }

    public EvalContext strict(Map<String, Object> variables) {
        return EvalContext.builder()
                .variables(new MapBackedVariables(variables))
                .build();
    }
}
