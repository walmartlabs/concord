package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Runs one iteration of a loop in the current frame (the iteration's own frame)
 * inside the iteration's log segment.
 */
public class LoopIterationCommand implements Command {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Uses the original command for processes compiled without iteration factories. */
    public static Command of(int index, Command cmd, Factory factory) {
        return factory != null ? factory.create(index) : cmd;
    }

    public static Factory segment(Command body, Step step, UUID correlationId) {
        return new SegmentFactory(body, step, correlationId);
    }

    public static Factory retry(RetryWrapper retry) {
        return new RetryFactory(retry);
    }

    /** Serializable recipe for a fresh command with the current iteration's index. */
    public interface Factory extends Serializable {
        Command create(int index);
    }

    private record SegmentFactory(Command body, Step step, UUID correlationId) implements Factory {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Command create(int index) {
            return new LoopIterationCommand(index, body, step, correlationId);
        }
    }

    private record RetryFactory(RetryWrapper retry) implements Factory {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Command create(int index) {
            return retry.withIterationIndex(index);
        }
    }

    private final int index;
    private final Command cmd;
    private final Step step;
    private final UUID correlationId;

    private LoopIterationCommand(int index, Command cmd, Step step, UUID correlationId) {
        this.index = index;
        this.cmd = cmd;
        this.step = step;
        this.correlationId = correlationId;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();
        frame.push(new CloseSegmentCommand(LogSegmentKind.ITERATION, correlationId));
        frame.push(cmd);
        frame.push(OpenSegmentCommand.iteration(step, correlationId, index));
    }
}
