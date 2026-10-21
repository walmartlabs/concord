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

import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.walmartlabs.concord.svm.Runtime;

/**
 * Closes the log segments whose {@link CloseSegmentCommand} is skipped:
 * the frame is removed early or its exception handler starts.
 */
public class LogSegmentLifecycleListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LogSegmentLifecycleListener.class);

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        updateSegments(runtime, state, ThreadStatus.SUSPENDED, LogSegmentStatus.SUSPENDED);
    }

    @Override
    public void beforeProcessResume(Runtime runtime, State state) {
        // VM has already woken the threads whose events were supplied, and their waiting parents.
        // Other branches still waiting for an event must remain suspended.
        updateSegments(runtime, state, ThreadStatus.READY, LogSegmentStatus.RUNNING);
    }

    @Override
    public void beforeProcessStart(Runtime runtime, State state) {
        // Runner also uses VM.start when continuing a saved state without a resume event.
        updateSegments(runtime, state, ThreadStatus.READY, LogSegmentStatus.RUNNING);
    }

    private static void updateSegments(Runtime runtime, State state, ThreadStatus threadStatus,
                                       LogSegmentStatus segmentStatus) {
        state.threadStatus().forEach((threadId, status) -> {
            if (status != threadStatus) {
                return;
            }
            LogSegmentUtils.getLogContexts(threadId, state).forEach(context -> {
                if (context == null || context.segmentId() == null) {
                    return;
                }
                try {
                    runtime.getService(RunnerLogger.class).setSegmentStatus(context.segmentId(), segmentStatus);
                } catch (Exception e) {
                    // A logging notification must not prevent suspension or resumption of the process.
                    log.warn("Failed to update log segment {} to {}", context.segmentId(), segmentStatus, e);
                }
            });
        });
    }


    @Override
    public void beforeFramePop(Runtime runtime, VM vm, State state, ThreadId threadId, Frame frame) {
        LogSegmentUtils.onFramePop(runtime, state, threadId, frame);
    }

    @Override
    public void beforeExceptionHandler(Runtime runtime, VM vm, State state, ThreadId threadId, Frame frame) {
        LogSegmentUtils.onExceptionHandler(runtime, state, threadId, frame);
    }
}
