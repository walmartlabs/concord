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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.UUID;

/**
 * Wraps the specified command into a new frame with an exception handler
 * consisting of a {@link ExposeLastErrorCommand} and the provided {@link #errorSteps}.
 * <p>
 * If the step has a log segment ({@link #correlationId} is set), the segment is opened in
 * the frame and stays open while the handler runs: the handler gets an {@code error}
 * segment inside it, and the step is {@code RECOVERED} unless the handler fails.
 */
public class ErrorWrapper implements Command {

    private static final long serialVersionUID = 1L;

    private final Command cmd;
    private final Command errorSteps;

    /**
     * {@code null} if the step has no log segment or in processes started by older runners.
     */
    private final Step step;
    private final UUID correlationId;
    private final boolean evaluateName;

    public ErrorWrapper(Command cmd, Command errorSteps) {
        this(cmd, errorSteps, null, null, false);
    }

    /**
     * @param evaluateName evaluate the step's name for the step's segment, see {@link OpenSegmentCommand}
     */
    public ErrorWrapper(Command cmd, Command errorSteps, Step step, UUID correlationId, boolean evaluateName) {
        this.cmd = cmd;
        this.errorSteps = errorSteps;
        this.step = step;
        this.correlationId = correlationId;
        this.evaluateName = evaluateName;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        // create a block of commands that looks like this:
        //  block:
        //    - ExposeLastErrorCommand
        //    - ...compiled steps from the "error" block...
        Frame.Builder inner = Frame.builder()
                .nonRoot();

        if (correlationId == null) {
            inner.exceptionHandler(new BlockCommand(new ExposeLastErrorCommand(), errorSteps))
                    .commands(cmd);
        } else {
            inner.exceptionHandler(new BlockCommand(new ExposeLastErrorCommand(),
                            new OpenSegmentCommand(LogSegmentKind.ERROR, step, correlationId, null, false, false),
                            errorSteps,
                            new CloseSegmentCommand(LogSegmentKind.ERROR, correlationId)))
                    // pushed in this order: the last command runs first
                    .commands(new CloseSegmentCommand(LogSegmentKind.STEP, correlationId),
                            cmd,
                            new OpenSegmentCommand(LogSegmentKind.STEP, step, correlationId, null, evaluateName, true));
        }

        state.pushFrame(threadId, inner.build());
    }
}
