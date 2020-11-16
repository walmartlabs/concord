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
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the specified list of variables from the source frame to the target frame.
 */
public class CopyVariablesCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final List<String> variables;
    private final Frame sourceFrame;
    private final Frame targetFrame;

    public CopyVariablesCommand(List<String> variables, Frame sourceFrame, Frame targetFrame) {
        this.variables = new ArrayList<>(variables);
        this.sourceFrame = sourceFrame;
        this.targetFrame = targetFrame;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        if (variables.isEmpty()) {
            return;
        }

        Frame effectiveSourceFrame = sourceFrame != null ? sourceFrame : frame;
        Frame effectiveTargetFrame = targetFrame != null ? targetFrame : frame;

        for (String variable : variables) {
            if (effectiveSourceFrame.hasLocal(variable)) {
                Serializable value = effectiveSourceFrame.getLocal(variable);
                VMUtils.putLocal(effectiveTargetFrame, variable, value);
            }
        }
    }
}
