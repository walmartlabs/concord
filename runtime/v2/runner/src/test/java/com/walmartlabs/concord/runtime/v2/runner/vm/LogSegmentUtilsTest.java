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

import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentAttributes;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.InMemoryState;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LogSegmentUtilsTest {

    /**
     * The state of a process suspended by a runner without log scopes.
     */
    @Test
    public void readsAStackOfLogSegmentsOnly() {
        State state = new InMemoryState(Frame.builder().root().build());
        ThreadId threadId = state.getRootThreadId();

        LogContext ctx = LogContext.builder()
                .segmentId(7L)
                .segmentName("step")
                .correlationId(UUID.randomUUID())
                .redirectSystemOutAndErr(false)
                .build();
        state.setThreadLocal(threadId, "logContext", new ArrayList<>(List.of(ctx)));

        assertSame(ctx, LogSegmentUtils.getLogContext(threadId, state));
        assertEquals(List.of(ctx), LogSegmentUtils.getLogContexts(threadId, state));

        LogSegmentAttributes attributes = LogSegmentUtils.getSegmentAttributes(threadId, state);
        assertEquals(7L, attributes.parentId());
        assertFalse(attributes.errorHandler());

        assertSame(ctx, LogSegmentUtils.popLogContext(threadId, state));
        assertNull(LogSegmentUtils.getLogContext(threadId, state));
    }
}
