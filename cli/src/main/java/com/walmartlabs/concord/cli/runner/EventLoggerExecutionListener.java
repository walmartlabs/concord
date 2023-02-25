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
import com.walmartlabs.concord.runtime.v2.runner.vm.StepCommand;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

public class EventLoggerExecutionListener implements ExecutionListener {

    @Override
    public Result beforeCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (!(cmd instanceof StepCommand)) {
            return Result.CONTINUE;
        }

        StepCommand<?> s = (StepCommand<?>) cmd;

        Location loc = s.getStep().getLocation();

        System.out.println(">>> '" + getDescription(s.getStep()) + "' at " + loc.fileName() + ":" + loc.lineNum());

        return Result.CONTINUE;
    }

    private static String getDescription(Step step) {
        // TODO: add 'description' into step? so we will not miss description for new steps...
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
