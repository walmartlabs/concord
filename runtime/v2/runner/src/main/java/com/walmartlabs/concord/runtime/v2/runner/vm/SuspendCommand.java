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
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Objects;

public class SuspendCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final String eventRef;

    public SuspendCommand(String eventRef) {
        this.eventRef = eventRef;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();
        state.setEventRef(threadId, eventRef);
        state.setStatus(threadId, ThreadStatus.SUSPENDED);

        // SUSPEND log segments
        RunnerLogger logger = runtime.getService(RunnerLogger.class);
        LogSegmentUtils.getLogContexts(threadId, state).stream()
                .filter(Objects::nonNull)
                .filter(c -> c.segmentId() != null)
                .forEach(c -> logger.setSegmentStatus(c.segmentId(), LogSegmentStatus.SUSPENDED));

        state.peekFrame(threadId).push(new ResumeLogSegmentsCommand());
    }
}
