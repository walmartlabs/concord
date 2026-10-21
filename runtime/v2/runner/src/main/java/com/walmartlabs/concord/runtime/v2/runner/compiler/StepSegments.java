package com.walmartlabs.concord.runtime.v2.runner.compiler;

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
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.CloseSegmentCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.ErrorWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.LogSegmentUtils;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoopIterationCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.OpenSegmentCommand;
import com.walmartlabs.concord.svm.Command;

import java.util.UUID;

/**
 * Builds the log segments of a step:
 * {@code Step(Error(Loop(Iteration(Retry(Attempt(body))))))}, where only the configured
 * wrappers exist. For loop + retry, iterations carry their index without opening a
 * segment; attempts are direct children of the step. See {@link LogSegmentUtils}.
 */
final class StepSegments {

    /**
     * The iteration segments of a step with {@code loop}. The step's name is evaluated
     * for each iteration unless retry attempts carry the iteration index instead.
     */
    static LoopIterationCommand.Segment iteration(Step step, UUID correlationId, boolean hasRetry) {
        return new LoopIterationCommand.Segment(step, correlationId, hasRetry);
    }

    /**
     * Wraps the (possibly looped or retried) body with the step's segment and its {@code error} block.
     *
     * @param aggregate   the body runs more than once (loop or retry): the step's name is
     *                    evaluated for each iteration or attempt instead
     * @param errorSteps  the compiled {@code error} block, {@code null} if none
     */
    static Command step(Command body, Step step, UUID correlationId, boolean aggregate, Command errorSteps) {
        if (errorSteps != null) {
            return new ErrorWrapper(body, errorSteps, step, correlationId, !aggregate);
        }

        return new BlockCommand(
                new OpenSegmentCommand(LogSegmentKind.STEP, step, correlationId, null, null, !aggregate, false),
                body,
                new CloseSegmentCommand(LogSegmentKind.STEP, correlationId));
    }

    private StepSegments() {
    }
}
