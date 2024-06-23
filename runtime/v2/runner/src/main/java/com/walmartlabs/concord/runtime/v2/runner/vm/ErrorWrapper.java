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

import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

/**
 * Wraps the specified command into a new frame with an exception handler
 * consisting of a {@link ExposeLastErrorCommand} and the provided {@link #errorSteps}.
 */
public class ErrorWrapper implements Command {

    private static final long serialVersionUID = 1L;

    private final Command cmd;
    private final Command errorSteps;

    public ErrorWrapper(Command cmd, Command errorSteps) {
        this.cmd = cmd;
        this.errorSteps = errorSteps;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // create a block of commands that looks like this:
        //  block:
        //    - ExposeLastErrorCommand
        //    - ...compiled steps from the "error" block...
        BlockCommand exceptionHandler = new BlockCommand(new ExposeLastErrorCommand(), errorSteps);

        Frame inner = Frame.builder()
                .nonRoot()
                .exceptionHandler(exceptionHandler)
                .commands(cmd)
                .build();

        state.pushFrame(threadId, inner);
    }
}
