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

import com.walmartlabs.concord.runtime.v2.model.IfStep;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

public class IfCommand extends StepCommand<IfStep> {

    private static final long serialVersionUID = 1L;

    private final Command thenCommand;
    private final Command elseCommand;

    public IfCommand(IfStep step, Command thenCommand, Command elseCommand) {
        super(step);
        this.thenCommand = thenCommand;
        this.elseCommand = elseCommand;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        IfStep step = getStep();
        String expr = step.getExpression();

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        Context ctx = runtime.getService(Context.class);
        EvalContext evalContext = ecf.global(ctx);

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Object ifResult = ee.eval(evalContext, expr, Object.class);
        if (isTrue(ifResult)) {
            frame.push(thenCommand);
        } else if (elseCommand != null) {
            frame.push(elseCommand);
        }
    }

    private static boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean b) {
            return b;
        } else if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) {
                return true;
            } else if ("false".equalsIgnoreCase(s)) {
                return false;
            }
        }

        throw new RuntimeException(String.format("Expected boolean value or string 'true'/'false', got: '%s', type: %s", value, value.getClass()));
    }
}
