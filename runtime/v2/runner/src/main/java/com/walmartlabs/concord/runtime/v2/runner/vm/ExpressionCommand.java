package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.Expression;
import com.walmartlabs.concord.runtime.v2.model.ExpressionOptions;
import com.walmartlabs.concord.runtime.v2.runner.script.VariablesSanitizer;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates the specified {@link Expression} step and (optionally) saves
 * the result as a global variable.
 */
public class ExpressionCommand extends StepCommand<Expression> {

    private static final long serialVersionUID = 1L;

    public ExpressionCommand(Expression step) {
        super(step);
    }

    @Override
    public Command copy() {
        return new ExpressionCommand(getStep());
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        Context ctx = runtime.getService(Context.class);

        Expression step = getStep();
        String expr = step.getExpr();

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Object result = ee.eval(ecf.global(ctx), expr, Object.class);
        result = VariablesSanitizer.sanitize(result);

        ExpressionOptions opts = Objects.requireNonNull(step.getOptions());
        if (!opts.outExpr().isEmpty()) {
            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
            Map<String, Object> vars = Collections.singletonMap("result", result);
            Map<String, Serializable> out = expressionEvaluator.evalAsMap(ecf.global(ctx, vars), opts.outExpr());
            out.forEach((k, v) -> ctx.variables().set(k, v));
        } else if (opts.out() != null) {
            ctx.variables().set(opts.out(), result);
        }
    }
}
