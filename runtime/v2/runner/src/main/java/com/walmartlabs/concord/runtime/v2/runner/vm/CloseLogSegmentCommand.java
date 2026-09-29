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

import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serial;
import java.util.UUID;

public class CloseLogSegmentCommand implements Command {

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
        if (logContext == null) {
            return;
        }
        Long segmentId = logContext.segmentId();
        if (segmentId == null) {
            return;
        }
        assert correlationId == logContext.correlationId();

        ThreadError threadError = state.getThreadError(threadId);
        LogSegmentStatus status = threadError != null ? LogSegmentStatus.ERROR : LogSegmentStatus.OK;
        if (threadError != null) {
            // the parent of the segments of the "error" block, if any
            LogSegmentUtils.setLastFailedSegment(threadId, state, threadError.exception(), segmentId);
        }

        runtime.getService(RunnerLogger.class)
                .setSegmentStatus(segmentId, status);
    }

}