package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Copies the specified list of variables from the source frame to the target frame.
 */
public class CopyVariablesCommand implements Command {

    public interface FrameProducer extends BiFunction<State, ThreadId, Frame>, Serializable {}

    private static final long serialVersionUID = 1L;

    private final List<String> variables;
    private final FrameProducer sourceFrameProducer;
    private final FrameProducer targetFrameProducer;

    public CopyVariablesCommand(List<String> variables, Frame sourceFrame, Frame targetFrame) {
        this(variables, (state, threadId) -> sourceFrame, (state, threadId) -> targetFrame);
    }

    public CopyVariablesCommand(List<String> variables, Frame sourceFrame, FrameProducer targetFrameProducer) {
        this(variables, (state, threadId) -> sourceFrame, targetFrameProducer);
    }

    public CopyVariablesCommand(List<String> variables, FrameProducer sourceFrameProducer, Frame targetFrame) {
        this(variables, sourceFrameProducer, (state, threadId) -> targetFrame);
    }

    public CopyVariablesCommand(List<String> variables, FrameProducer sourceFrameProducer, FrameProducer targetFrameProducer) {
        this.variables = variables;
        this.sourceFrameProducer = sourceFrameProducer;
        this.targetFrameProducer = targetFrameProducer;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        if (variables.isEmpty()) {
            return;
        }

        Frame effectiveSourceFrame = sourceFrameProducer.apply(state, threadId);
        Frame effectiveTargetFrame = targetFrameProducer.apply(state, threadId);

        for (String variable : variables) {
            if (effectiveSourceFrame.hasLocal(variable)) {
                Serializable value = effectiveSourceFrame.getLocal(variable);
                VMUtils.putLocal(effectiveTargetFrame, variable, value);
            }
        }
    }
}
