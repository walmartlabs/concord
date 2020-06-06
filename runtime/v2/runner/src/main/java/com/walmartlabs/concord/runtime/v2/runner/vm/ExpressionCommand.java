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
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;

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
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        Context ctx = runtime.getService(Context.class);

        Expression step = getStep();
        String expr = step.getExpr();

        ExpressionOptions opts = step.getOptions();
        String out = opts != null ? opts.out() : null;

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Object v = ee.eval(EvalContextFactory.global(ctx), expr, Object.class);

        if (out != null) {
            if (v != null && !(v instanceof Serializable)) {
                String msg = String.format("The expression's (%s) result is not a Serializable value, it cannot be saved as a flow variable: %s", expr, v.getClass());
                throw new IllegalArgumentException(msg);
            }

            frame.setLocal(out, (Serializable) v);

            // TODO common structure for results
        }
    }

    @Override
    protected String getSegmentName(Context ctx, Expression step) {
        return "expression";
    }
}
