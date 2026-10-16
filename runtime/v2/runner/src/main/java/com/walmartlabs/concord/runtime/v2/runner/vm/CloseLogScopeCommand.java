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

import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serial;
import java.util.UUID;

/**
 * Closes a log scope, see {@link LogSegmentUtils}. Used as a {@code finally} handler.
 */
public class CloseLogScopeCommand implements Command {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID scopeId;

    public CloseLogScopeCommand(UUID scopeId) {
        this.scopeId = scopeId;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();
        LogSegmentUtils.closeScope(threadId, state, scopeId);
    }
}
