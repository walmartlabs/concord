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

import com.sun.corba.se.impl.protocol.giopmsgheaders.FragmentMessage;
import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.model.FlowCallOptions;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerUtils;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContext;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class FlowCallCommand extends StepCommand<FlowCall> {

    private static final long serialVersionUID = 1L;

    public FlowCallCommand(FlowCall step) {
        super(step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        Context ctx = runtime.getService(Context.class);

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        EvalContext evalCtx = EvalContextFactory.global(ctx);

        FlowCall call = getStep();
        FlowCallOptions opts = call.getOptions();

        // the called flow's name
        String flowName = ee.eval(evalCtx, call.getFlowName(), String.class);

        // the called flow's steps
        Compiler compiler = runtime.getService(Compiler.class);
        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);
        ProcessConfiguration pc = runtime.getService(ProcessConfiguration.class);

        Command steps = CompilerUtils.compile(compiler, pc, pd, flowName);

        Map<String, Object> input = VMUtils.prepareInput(ee, ctx, Objects.requireNonNull(opts).input());

        // the call's frame should be a "root" frame
        // all local variables will have this frame as their base
        Frame innerFrame = Frame.builder()
                .root()
                .commands(steps)
                .locals(input)
                .build();

        // an "out" handler:
        // grab the out variable from the called flow's frame
        // and put it into the callee's frame
        Command processOutVars;
        if (!opts.outExpr().isEmpty()) {
            processOutVars = new EvalVariablesCommand(ctx, opts.outExpr(), innerFrame);
        } else {
            processOutVars = new CopyVariablesCommand(opts.out(), innerFrame, null);
        }

        // push the out handler first so it executes after the called flow's frame is done
        Frame outerFrame = state.peekFrame(threadId);
        outerFrame.push(processOutVars);
        outerFrame.push(new Command() { // TODO
            @Override
            public void eval(Runtime runtime, State state, ThreadId threadId) throws Exception {
                Frame f = state.peekFrame(threadId);
                f.pop();
                f.removeLocal("__parentSegmentId");
            }
        });

        state.pushFrame(threadId, innerFrame);
    }

    private static class EvalVariablesCommand implements Command {

        private final Context ctx;
        private final Map<String, Serializable> variables;
        private final Frame variablesFrame;

        private EvalVariablesCommand(Context ctx, Map<String, Serializable> variables, Frame variablesFrame) {
            this.ctx = ctx;
            this.variables = variables;
            this.variablesFrame = variablesFrame;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void eval(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
            Map<String, Object> vars = (Map)variablesFrame.getLocals();
            Map<String, Serializable> out = expressionEvaluator.evalAsMap(EvalContextFactory.global(ctx, vars), variables);
            out.forEach((k, v) -> ctx.variables().set(k, v));
        }
    }
}
