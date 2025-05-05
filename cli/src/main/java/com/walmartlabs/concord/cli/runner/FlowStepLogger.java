package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.vm.LogSegmentScopeCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.StepCommand;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import static org.fusesource.jansi.Ansi.ansi;

public class FlowStepLogger implements ExecutionListener {

    @Override
    public Result beforeCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (!(cmd instanceof StepCommand)) {
            return Result.CONTINUE;
        }

        if (cmd instanceof LogSegmentScopeCommand) {
            return Result.CONTINUE;
        }

        StepCommand<?> s = (StepCommand<?>) cmd;

        Location loc = s.getStep().getLocation();

        System.out.println(ansi().fgBrightCyan().bold().a(">>> '").a(getDescription(runtime, state, threadId, s.getStep())).boldOff()
                .a("' @ ").a(loc.fileName()).a(":").a(loc.lineNum()).reset());

        return Result.CONTINUE;
    }

    private static String getDescription(Runtime runtime, State state, ThreadId threadId, Step step) {
        if (step instanceof AbstractStep) {
            ContextFactory contextFactory = runtime.getService(ContextFactory.class);
            Context ctx = contextFactory.create(runtime, state, threadId, step);

            String rawSegmentName = SegmentedLogger.getSegmentName((AbstractStep<?>) step);
            String segmentName = ctx.eval(rawSegmentName, String.class);
            if (segmentName != null) {
                return segmentName;
            }
        }

        return getDefaultDescription(step);
    }

    private static String getDefaultDescription(Step step) {
        if (step instanceof FlowCall) {
            return "Flow call: " + ((FlowCall) step).getFlowName();
        } else if (step instanceof Expression) {
            return "Expression: " + ((Expression) step).getExpr();
        } else if (step instanceof ScriptCall) {
            return "Script: " + ((ScriptCall) step).getLanguageOrRef();
        } else if (step instanceof IfStep) {
            return "Check: " + ((IfStep) step).getExpression();
        } else if (step instanceof SwitchStep) {
            return "Switch: " + ((SwitchStep) step).getExpression();
        } else if (step instanceof SetVariablesStep) {
            return "Set variables";
        } else if (step instanceof Checkpoint) {
            return "Checkpoint: " + ((Checkpoint) step).getName();
        } else if (step instanceof FormCall) {
            return "Form call: " + ((FormCall) step).getName();
        } else if (step instanceof GroupOfSteps) {
            return "Group of steps";
        } else if (step instanceof ParallelBlock) {
            return "Parallel block";
        } else if (step instanceof ExitStep) {
            return "Exit";
        } else if (step instanceof ReturnStep) {
            return "Return";
        } else if (step instanceof TaskCall) {
            return "Task: " + ((TaskCall) step).getName();
        }

        return step.getClass().getName();
    }
}
