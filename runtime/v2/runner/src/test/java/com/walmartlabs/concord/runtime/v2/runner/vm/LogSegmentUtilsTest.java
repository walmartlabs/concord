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
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.InMemoryState;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.ThreadStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

        assertEquals(7L, LogSegmentUtils.getParentId(threadId, state));

        assertSame(ctx, LogSegmentUtils.popLogContext(threadId, state));
        assertNull(LogSegmentUtils.getLogContext(threadId, state));
    }
    @Test
    public void publishedStacksRemainUnchangedWhileOpeningAndUnwinding() {
        var state = new InMemoryState(Frame.builder().root().build());
        var thread = state.getRootThreadId();
        var runtime = mock(Runtime.class);
        var logger = mock(RunnerLogger.class);
        when(runtime.getService(RunnerLogger.class))
                .thenReturn(logger);
        var correlationId = UUID.randomUUID();
        LogSegmentUtils.register(state, thread, context(1L, "outer", correlationId), LogSegmentKind.STEP, true);
        ArrayList<?> first = state.getThreadLocal(thread, "logContext");
        LogSegmentUtils.register(state, thread, context(2L, "inner", correlationId), LogSegmentKind.STEP, false);
        ArrayList<?> second = state.getThreadLocal(thread, "logContext");
        assertEquals(1, first.size());
        assertEquals(2, second.size());
        var secondSnapshot = new ArrayList<>(second);
        LogSegmentUtils.register(state, thread, context(3L, "innermost", correlationId), LogSegmentKind.STEP, false);
        var published = new ArrayList<ArrayList<?>>();
        var snapshots = new ArrayList<ArrayList<?>>();
        doAnswer(invocation -> {
            ArrayList<?> stack = state.getThreadLocal(thread, "logContext");
            if (stack != null) {
                published.add(stack);
                snapshots.add(new ArrayList<>(stack));
            }
            return null;
        }).when(logger).setSegmentStatus(anyLong(), any(LogSegmentStatus.class));
        LogSegmentUtils.onExceptionHandler(runtime, state, thread, state.peekFrame(thread));
        assertEquals(List.of(2, 1), published.stream().map(List::size).toList());
        assertEquals(snapshots, published);
        assertEquals(secondSnapshot, second);
        ArrayList<?> failed = state.getThreadLocal(thread, "logContext");
        assertEquals(1, failed.size());
        assertNotEquals(first, failed);
        LogSegmentUtils.onFramePop(runtime, state, thread, state.peekFrame(thread));
        assertEquals(1, failed.size());
        assertNull(state.getThreadLocal(thread, "logContext"));
    }

    @Test
    public void statusFailureDoesNotInterruptFrameCleanup() {
        var state = new InMemoryState(Frame.builder().root().build());
        var thread = state.getRootThreadId();
        var runtime = mock(Runtime.class);
        var logger = mock(RunnerLogger.class);
        when(runtime.getService(RunnerLogger.class)).thenReturn(logger);
        var correlationId = UUID.randomUUID();
        LogSegmentUtils.register(state, thread, context(1L, "outer", correlationId), LogSegmentKind.STEP, false);
        LogSegmentUtils.register(state, thread, context(2L, "inner", correlationId), LogSegmentKind.STEP, false);
        doThrow(new IllegalStateException("status unavailable"))
                .when(logger).setSegmentStatus(2L, LogSegmentStatus.OK);
        LogSegmentUtils.onFramePop(runtime, state, thread, state.peekFrame(thread));
        assertNull(LogSegmentUtils.getLogContext(thread, state));
        verify(logger).setSegmentStatus(1L, LogSegmentStatus.OK);
    }
    @Test
    public void readsAndUnrelatedFrameCleanupDoNotCopyPublishedStack() {
        var state = new InMemoryState(Frame.builder().root().build());
        var thread = state.getRootThreadId();
        var correlationId = UUID.randomUUID();
        var ctx = context(1L, "parent", correlationId);
        LogSegmentUtils.register(state, thread, ctx, LogSegmentKind.STEP, false);
        ArrayList<java.io.Serializable> items = state.getThreadLocal(thread, "logContext");
        var published = new ArrayList<java.io.Serializable>(items) {
            @Override
            public Object[] toArray() {
                fail("Read-only operations must not copy the published stack");
                return null;
            }
        };
        state.setThreadLocal(thread, "logContext", published);
        var childFrame = Frame.builder().nonRoot().build();
        state.pushFrame(thread, childFrame);
        var runtime = mock(Runtime.class);

        assertSame(ctx, LogSegmentUtils.getLogContext(thread, state));
        assertEquals(List.of(ctx), LogSegmentUtils.getLogContexts(thread, state));
        LogSegmentUtils.onFramePop(runtime, state, thread, childFrame);
        LogSegmentUtils.onExceptionHandler(runtime, state, thread, childFrame);
        LogSegmentUtils.close(runtime, state, thread, LogSegmentKind.STEP, correlationId);

        assertSame(published, state.getThreadLocal(thread, "logContext"));
        verifyNoInteractions(runtime);
    }

    @Test
    public void lifecycleUpdatesOwnedSegmentsOfMatchingThreadsOnly() {
        var state = new InMemoryState(Frame.builder().root().build());
        var root = state.getRootThreadId();
        var child = state.nextThreadId();
        state.fork(root, child);
        var runtime = mock(Runtime.class);
        var logger = mock(RunnerLogger.class);
        when(runtime.getService(RunnerLogger.class)).thenReturn(logger);
        LogSegmentUtils.register(state, root, context(1, "parent", UUID.randomUUID()), LogSegmentKind.STEP, false);
        LogSegmentUtils.inherit(root, child, state);
        LogSegmentUtils.register(state, child, context(2, "child", UUID.randomUUID()), LogSegmentKind.STEP, false);
        state.setStatus(root, ThreadStatus.SUSPENDED);
        state.setStatus(child, ThreadStatus.SUSPENDED);
        var listener = new LogSegmentLifecycleListener();

        listener.afterProcessEnds(runtime, state, null);
        verify(logger).setSegmentStatus(1, LogSegmentStatus.SUSPENDED);
        verify(logger).setSegmentStatus(2, LogSegmentStatus.SUSPENDED);
        verifyNoMoreInteractions(logger);
        clearInvocations(logger);

        state.setStatus(root, ThreadStatus.READY);
        listener.beforeProcessResume(runtime, state);
        verify(logger).setSegmentStatus(1, LogSegmentStatus.RUNNING);
        verifyNoMoreInteractions(logger);
    }

    private static LogContext context(long id, String name, UUID correlationId) {
        return LogContext.builder()
                .segmentId(id)
                .segmentName(name)
                .correlationId(correlationId)
                .redirectSystemOutAndErr(false)
                .build();
    }

}
