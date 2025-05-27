package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.runner.vm.FlowCallCommand;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

public class StackTraceCollector implements ExecutionListener {

    @Override
    public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (cmd instanceof FlowCallCommand) {
            FlowCallCommand fcc = (FlowCallCommand) cmd;
            FlowCall step = fcc.getStep();
            String flowName = FlowCallCommand.getFlowName(state, threadId);
            if (flowName == null) {
                flowName = step.getFlowName();
            }
            Location location = step.getLocation();
            FrameId frameId = state.peekFrame(threadId).id();
            state.pushStackTraceItem(threadId, new StackTraceItem(frameId, threadId, location.fileName(), flowName, location.lineNum(), location.column()));
            return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
        }

        Frame frame = state.peekFrame(threadId);
        if (frame == null) {
            state.clearStackTrace(threadId);
            return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
        }

        return ExecutionListener.super.afterCommand(runtime, vm, state, threadId, cmd);
    }
}
