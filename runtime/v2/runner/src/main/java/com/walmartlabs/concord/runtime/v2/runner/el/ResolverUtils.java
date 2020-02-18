package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.sun.el.lang.EvaluationContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;

import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

public final class ResolverUtils {

    public static Context getContext(ELContext context) {
        VariableMapper varMapper = ((EvaluationContext) context).getELContext().getVariableMapper();

        ValueExpression v = varMapper.resolveVariable(Constants.Context.CONTEXT_KEY);
        if (v != null) {
            return (Context) v.getValue(context);
        }

        throw new IllegalStateException("Can't find the Concord context variable in the current ELContext: " + context);
    }

    public static Object getVariable(ELContext context, String name) {
        VariableMapper varMapper = ((EvaluationContext) context).getELContext().getVariableMapper();

        ValueExpression v = varMapper.resolveVariable(name);
        if (v != null) {
            return v.getValue(context);
        }

        v = varMapper.resolveVariable(Constants.Context.CONTEXT_KEY);
        if (v != null) {
            Context ctx = (Context) v.getValue(context);
            return ctx.globalVariables().get(name);
        }

        return null;
    }

    private ResolverUtils() {
    }
}
