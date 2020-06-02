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

import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.model.FlowCallOptions;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerUtils;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContext;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Map;

public class FlowCallCommand extends StepCommand<FlowCall> {

    private static final long serialVersionUID = 1L;

    private final ProcessDefinition pd;

    public FlowCallCommand(FlowCall step, ProcessDefinition pd) {
        super(step);
        this.pd = pd;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep());
        EvalContext evalCtx = EvalContextFactory.global(ctx);

        FlowCall call = getStep();

        FlowCallOptions opts = call.getOptions();

        // the called flow's name
        String flowName = expressionEvaluator.eval(evalCtx, call.getFlowName(), String.class);

        // the called flow's steps
        Compiler compiler = runtime.getService(Compiler.class);
        Command steps = CompilerUtils.compile(compiler, pd, flowName);

        Map<String, Object> input = VMUtils.prepareInput(expressionEvaluator, ctx, opts.input());

        // the call's frame should be a "root" frame
        // all local variables will have this frame as their base
        Frame innerFrame = Frame.builder()
                .root()
                .commands(steps)
                .locals(input)
                .build();

        // an "out" handler

        String outVar = opts.out(); // TODO support for multiple out variables
        Command processOutVars = new ProcessOutVariablesCommand(outVar, innerFrame);

        // push the out handler first so it executes after the called flow's frame is done
        state.peekFrame(threadId).push(processOutVars);
        state.pushFrame(threadId, innerFrame);
    }

    public static class ProcessOutVariablesCommand implements Command {

        private static final long serialVersionUID = 1L;

        private final String outVar;
        private final Frame innerFrame;

        public ProcessOutVariablesCommand(String outVar, Frame innerFrame) {
            this.outVar = outVar;
            this.innerFrame = innerFrame;
        }  // TODO refactor

        @Override
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame outerFrame = state.peekFrame(threadId);
            outerFrame.pop();

            if (outVar == null) {
                return;
            }

            // grab the out variable from the called flow's frame
            if (innerFrame.hasLocal(outVar)) {
                Object v = innerFrame.getLocal(outVar);
                // and put it into the callee's frame
                VMUtils.putLocal(outerFrame, outVar, v);
            }
        }
    }
}
