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

import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerUtils;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class FlowCallCommand extends StepCommand<FlowCall> implements ElementEventProducer {

    private static final String FLOW_NAME_VARIABLE = "__flowName__b6bc6c58-c2bc-434c-9a6b-b0092237720b";

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(FlowCallCommand.class);

    public FlowCallCommand(UUID correlationId, FlowCall step) {
        super(correlationId, step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        Context ctx = runtime.getService(Context.class);

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        EvalContext evalCtx = ecf.global(ctx);

        FlowCall call = getStep();

        // the called flow's name
        String flowName = ee.eval(evalCtx, call.getFlowName(), String.class);

        // the called flow's steps
        Compiler compiler = runtime.getService(Compiler.class);
        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);
        ProcessConfiguration pc = runtime.getService(ProcessConfiguration.class);

        Command steps = CompilerUtils.compile(compiler, pc, pd, flowName);

        FlowCallOptions opts = Objects.requireNonNull(call.getOptions());
        Map<String, Object> input = VMUtils.prepareInput(ecf, ee, ctx, opts.input(), opts.inputExpression(), false);

        // the call's frame should be a "root" frame
        // all local variables will have this frame as their base
        Frame innerFrame = Frame.builder()
                .root()
                .commands(steps)
                .locals(input)
                .build();

        Command processOutVars = outCommandOrNull(opts, ee, evalCtx, innerFrame);

        // push the out handler first so it executes after the called flow's frame is done
        if (processOutVars != null) {
            state.peekFrame(threadId).push(processOutVars);
        }
        state.pushFrame(threadId, innerFrame);
        VMUtils.putLocal(innerFrame, FLOW_NAME_VARIABLE, flowName);
    }

    public static String getFlowName(State state, ThreadId threadId) {
        return VMUtils.getLocal(state, threadId, FLOW_NAME_VARIABLE);
    }

    @Override
    public String getDescription(State state, ThreadId threadId) {
        return "Flow call: " + getFlowName(state, threadId);
    }

    private Command outCommandOrNull(FlowCallOptions opts, ExpressionEvaluator ee, EvalContext evalCtx, Frame innerFrame) {
        if (opts.outExpression() != null) {
            Object outExpr = ee.eval(evalCtx, opts.outExpression(), Object.class);
            if (outExpr instanceof List<?> l) {
                return new CopyVariablesCommand(l.stream().filter(Objects::nonNull).map(Object::toString).toList(), innerFrame, VMUtils::assertNearestRoot);
            } else if (outExpr instanceof Map<?,?> m) {
                Map<String, Serializable> mm = m.entrySet().stream()
                        .filter(e -> e.getKey() != null)
                        .filter(e -> e.getValue() instanceof Serializable)
                        .collect(Collectors.toMap(
                                e -> e.getKey().toString(),
                                e -> (Serializable)e.getValue(),
                                (v1, v2) -> v1));
                return new EvalVariablesCommand(getStep(), mm, innerFrame);
            } else if (outExpr != null){
                log.warn("Unexpected out expr type: {}, expected list or map", outExpr.getClass());
            }
            return null;
        } else if (opts.outMapping() != null && !opts.outMapping().isEmpty()) {
            return new EvalVariablesCommand(getStep(), opts.outMapping(), innerFrame);
        } else if (opts.outExpr() != null && !opts.outExpr().isEmpty()) {
            return new EvalVariablesCommand(getStep(), opts.outExpr(), innerFrame);
        } else {
            return new CopyVariablesCommand(opts.out(), innerFrame, VMUtils::assertNearestRoot);
        }
    }

    private static class EvalVariablesCommand extends StepCommand<FlowCall> {

        // for backward compatibility (java8 concord 1.92.0 version)
        private static final long serialVersionUID = -7294220776008029488L;

        // TODO: only for backward compatibility
        private final FlowCall step;

        private final Map<String, Serializable> variables;
        private final Frame variablesFrame;

        private EvalVariablesCommand(FlowCall step, Map<String, Serializable> variables, Frame variablesFrame) {
            super(step);
            this.step = Objects.requireNonNull(step);
            this.variables = Objects.requireNonNull(variables);
            this.variablesFrame = Objects.requireNonNull(variablesFrame);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected void execute(Runtime runtime, State state, ThreadId threadId) {
            Frame frame = state.peekFrame(threadId);
            frame.pop();

            Context ctx = runtime.getService(Context.class);

            EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
            Map<String, Object> vars = (Map) variablesFrame.getLocals();
            Map<String, Serializable> out = expressionEvaluator.evalAsMap(ecf.global(ctx, vars), variables);
            out.forEach((k, v) -> ctx.variables().set(k, v));
        }

        // TODO: only for backward compatibility
        @Override
        public FlowCall getStep() {
            return step;
        }
    }
}
