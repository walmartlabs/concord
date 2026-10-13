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

import com.walmartlabs.concord.svm.*;
import com.walmartlabs.concord.svm.Runtime;

/**
 * Closes the log segments whose {@link CloseSegmentCommand} is skipped:
 * the frame is removed early or its exception handler starts.
 */
public class LogSegmentLifecycleListener implements ExecutionListener {

    @Override
    public void beforeFramePop(Runtime runtime, VM vm, State state, ThreadId threadId, Frame frame) {
        LogSegmentUtils.onFramePop(runtime, state, threadId, frame);
    }

    @Override
    public void beforeExceptionHandler(Runtime runtime, VM vm, State state, ThreadId threadId, Frame frame) {
        LogSegmentUtils.onExceptionHandler(runtime, state, threadId, frame);
    }
}
