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
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContext;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

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
        Command processOutVars = new CopyVariablesCommand(opts.out(), innerFrame, null);

        // push the out handler first so it executes after the called flow's frame is done
        state.peekFrame(threadId).push(processOutVars);
        state.pushFrame(threadId, innerFrame);
    }
}
