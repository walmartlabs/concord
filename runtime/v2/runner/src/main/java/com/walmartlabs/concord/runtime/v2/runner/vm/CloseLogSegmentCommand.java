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

import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serial;
import java.util.UUID;

public class CloseLogSegmentCommand implements FinalCommand {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID correlationId;

    public CloseLogSegmentCommand(UUID correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        LogContext logContext = LogSegmentUtils.popLogContext(threadId, state);
        if (logContext == null || logContext.segmentId() == null) {
            return;
        }

        assert correlationId == logContext.correlationId();

//        runtime.getService(RunnerLogger.class)
//                .setSegmentStatus(logContext.segmentId(), getStatus(state, threadId));
        runtime.getService(RunnerLogger.class)
                .setSegmentStatus(logContext.segmentId(), RunnerLogger.SegmentStatus.OK);
    }

    private static RunnerLogger.SegmentStatus getStatus(State state, ThreadId threadId) {
        if (VMUtils.getLocal(state, threadId, Frame.LAST_EXCEPTION_KEY) != null) {
            return RunnerLogger.SegmentStatus.ERROR;
        } else {
            return RunnerLogger.SegmentStatus.OK;
        }
    }

    @Override
    public void onError(Runtime runtime, State state, ThreadId threadId, Throwable error) {
        state.peekFrame(threadId).pop();

        LogContext logContext = LogSegmentUtils.popLogContext(threadId, state);
        if (logContext == null || logContext.segmentId() == null) {
            return;
        }

        assert correlationId == logContext.correlationId();

        runtime.getService(RunnerLogger.class)
                .setSegmentStatus(logContext.segmentId(), RunnerLogger.SegmentStatus.ERROR);
    }

}
