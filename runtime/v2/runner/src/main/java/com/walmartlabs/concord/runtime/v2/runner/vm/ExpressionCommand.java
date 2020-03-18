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
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.UUID;

/**
 * Evaluates the specified {@link Expression} step and (optionally) saves
 * the result as a global variable.
 */
public class ExpressionCommand extends StepCommand<Expression> {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ExpressionCommand.class);

    public ExpressionCommand(Expression step) {
        super(step);
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        Expression step = getStep();
        String expr = step.getExpr();

        ExpressionOptions opts = step.getOptions();
        String out = opts != null ? opts.out() : null;

        ThreadLocalContext.set(ctx);
        try {
            Object v = ee.eval(ctx, expr, Object.class);
            if (out != null) {
                if (v != null && !(v instanceof Serializable)) {
                    log.warn("The expression's ('{}') result is not Serializable, it won't be preserved between process executions: {}", expr, v.getClass());
                }

                GlobalVariables gv = runtime.getService(GlobalVariables.class);
                gv.put(out, v);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            ThreadLocalContext.clear();
        }
    }
}
