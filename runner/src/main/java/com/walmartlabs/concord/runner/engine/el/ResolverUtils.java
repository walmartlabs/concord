package com.walmartlabs.concord.runner.engine.el;

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
import com.walmartlabs.concord.project.InternalConstants;
import io.takari.bpm.api.ExecutionContext;

import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

public final class ResolverUtils {

    public static Object getVariable(ELContext context, String name) {
        VariableMapper varMapper = ((EvaluationContext) context).getELContext().getVariableMapper();
        ValueExpression v = varMapper.resolveVariable(name);
        if(v != null) {
            return v.getValue(context);
        }
        v = varMapper.resolveVariable(InternalConstants.Context.CONTEXT_KEY);
        if(v != null) {
            ExecutionContext ctx = (ExecutionContext) v.getValue(context);
            return ctx.getVariable(name);
        }
        return null;
    }

    private ResolverUtils() {
    }
}
