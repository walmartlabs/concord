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
import com.walmartlabs.concord.runtime.v2.model.Retry;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.vm.BlockCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.CloseSegmentCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.ErrorWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.LogSegmentUtils;
import com.walmartlabs.concord.runtime.v2.runner.vm.LogSegmentScopeCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.RetryWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.WithItemsWrapper;
import com.walmartlabs.concord.runtime.v2.runner.vm.LoopIterationCommand;
import com.walmartlabs.concord.runtime.v2.runner.vm.OpenSegmentCommand;
import com.walmartlabs.concord.svm.Command;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the log segments of a step:
 * {@code Step(Error(Loop(Iteration(Retry(Attempt(body))))))}, where only the configured
 * wrappers exist. For loop + retry, iterations carry their index without opening a
 * segment; attempts are direct children of the step. See {@link LogSegmentUtils}.
 */
final class StepSegments {

    /**
     * Creates a segment around each iteration of a plain loop.
     */
    static LoopIterationCommand.Factory iteration(Command body, Step step, UUID correlationId) {
        return LoopIterationCommand.segment(body, step, correlationId);
    }

    /**
     * Wraps the (possibly looped or retried) body with the step's segment and its {@code error} block.
     *
     * @param kind        STEP, LOOP or RETRY; repeated steps evaluate their names in each run
     * @param errorSteps  the compiled {@code error} block, {@code null} if none
     */
    static Command step(Command body, Step step, UUID correlationId, LogSegmentKind kind, Command errorSteps) {
        if (errorSteps != null) {
            return new ErrorWrapper(body, errorSteps, step, correlationId, kind);
        }

        return segment(body, OpenSegmentCommand.step(step, correlationId, kind), kind, correlationId);
    }

    static Command parallel(Command body, Step step, UUID correlationId) {
        return segment(body, OpenSegmentCommand.parallel(step, correlationId), LogSegmentKind.PARALLEL, correlationId);
    }

    /** A segment around a body that has no error handler of its own. */
    private static Command segment(Command body, OpenSegmentCommand open, LogSegmentKind kind, UUID correlationId) {
        return new BlockCommand(
                open,
                body,
                new CloseSegmentCommand(kind, correlationId));
    }

    /** Legacy withItems: keep per-item scopes without the new iteration or attempt segments. */
    static Command withItems(Command body, Step step, UUID correlationId, WithItems items, Retry retry,
                             Collection<String> out, Map<String, Serializable> outMapping, Command errorSteps) {
        Command cmd = new LogSegmentScopeCommand<>(correlationId, body, step);
        if (retry != null) {
            cmd = new RetryWrapper(cmd, retry, step);
        }
        cmd = WithItemsWrapper.of(cmd, items, out, outMapping, step);
        return errorSteps != null ? new ErrorWrapper(cmd, errorSteps) : cmd;
    }

    private StepSegments() {
    }
}
