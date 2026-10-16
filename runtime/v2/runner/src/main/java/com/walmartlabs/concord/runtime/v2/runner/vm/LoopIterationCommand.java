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

    /**
     * Returns the command itself if the iterations have no log segments.
     */
    public static Command of(int index, Command cmd, Segment segment) {
        return segment != null ? new LoopIterationCommand(index, cmd, segment) : cmd;
    }

    private final int index;
    private final Command cmd;
    private final Segment segment;

    private LoopIterationCommand(int index, Command cmd, Segment segment) {
        this.index = index;
        this.cmd = cmd;
        this.segment = segment;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        frame.push(new CloseSegmentCommand(segment.kind(), segment.correlationId()));
        frame.push(cmd);
        frame.push(new OpenSegmentCommand(segment.kind(), segment.step(), segment.correlationId(), index,
                segment.evaluateName(), false));
    }

    /**
     * @param correlationId the step's correlation ID
     * @param kind          ITERATION or ITERATION_RETRY, known before the iteration starts
     */
    public record Segment(Step step, UUID correlationId, LogSegmentKind kind) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public boolean evaluateName() {
            return kind == LogSegmentKind.ITERATION;
        }
    }
}
