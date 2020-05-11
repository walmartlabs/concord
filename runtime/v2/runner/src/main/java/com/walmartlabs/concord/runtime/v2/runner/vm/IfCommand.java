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
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContext;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.UUID;

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

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        IfStep step = getStep();
        String expr = step.getExpression();

        EvalContext evalContext = EvalContextFactory.global(ctx);
        boolean ifResult = ThreadLocalContext.withContext(ctx, () -> ee.eval(evalContext, expr, Boolean.class));
        if (ifResult) {
            frame.push(thenCommand);
        } else if (elseCommand != null) {
            frame.push(elseCommand);
        }
    }
}
