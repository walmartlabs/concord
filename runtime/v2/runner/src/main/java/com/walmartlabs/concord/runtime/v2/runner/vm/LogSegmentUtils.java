package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import ch.qos.logback.classic.Level;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentAttributes;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.FrameId;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.ThreadStatus;

import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The open log segments of a thread, innermost last.
 * <p>
 * A segment belongs to the frame it was opened in. It is closed by {@link CloseSegmentCommand}
 * or, if the command is skipped (an error, a {@code return}), when the frame is removed or
 * its exception handler starts, see {@link LogSegmentLifecycleListener}.
 * <p>
 * The stack may contain plain {@link LogContext} items: segments opened by
 * {@link LogSegmentScopeCommand} in processes started by older runners.
 */
public final class LogSegmentUtils {

    private static final String KEY = "logContext";

    private static final long SYSTEM_SEGMENT_ID = 0;

    /**
     * Opens a segment owned by the current frame. The parent is the innermost open segment.
     *
     * @param keepOnHandler {@code true} if the segment stays open when the exception handler
     *                      of the frame starts: the segment of the step guarded by the handler
     * @return {@code null} if segmented logging is disabled
     */
    @Nullable
    public static LogContext open(Runtime runtime, State state, ThreadId threadId,
                                  LogSegmentKind kind, String name, UUID correlationId,
                                  @Nullable Integer iterationIndex, @Nullable Integer attemptIndex, Level logLevel, boolean redirectSystemOutAndErr,
                                  boolean keepOnHandler) {

        LogSegmentAttributes attributes = new LogSegmentAttributes(getParentId(threadId, state), kind, iterationIndex, attemptIndex, getThread(state, threadId));
        Long segmentId = runtime.getService(RunnerLogger.class).createSegment(name, correlationId, attributes);
        if (segmentId == null) {
            return null;
        }

        LogContext ctx = LogContext.builder()
                .segmentId(segmentId)
                .segmentName(name)
                .correlationId(correlationId)
                .logLevel(logLevel)
                .redirectSystemOutAndErr(redirectSystemOutAndErr)
                .build();

        FrameId owner = state.peekFrame(threadId).id();
        push(threadId, state, new OpenSegment(ctx, kind, owner, keepOnHandler, false, false));
        return ctx;
    }

    /**
     * Closes the innermost segment if it's the specified segment of the current frame.
     * Does nothing if the segment is already closed.
     */
    public static void close(Runtime runtime, State state, ThreadId threadId, LogSegmentKind kind, UUID correlationId) {
        FrameId frameId = state.peekFrame(threadId).id();
        ArrayList<Serializable> items = getItems(threadId, state);
        if (items.isEmpty() || !(items.get(items.size() - 1) instanceof OpenSegment s)) {
            return;
        }

        if (s.inherited() || !s.owner().equals(frameId) || s.kind() != kind || !s.context().correlationId().equals(correlationId)) {
            return;
        }

        pop(runtime, state, threadId, items, s.failed() ? LogSegmentStatus.ERROR : LogSegmentStatus.OK);
    }

    /**
     * Closes the segments of the frame being removed: {@code ERROR} if the stack is unwinding,
     * or the segment failed before entering its error handler; otherwise {@code OK}.
     */
    public static void onFramePop(Runtime runtime, State state, ThreadId threadId, Frame frame) {
        boolean failed = state.getStatus(threadId) == ThreadStatus.UNWINDING;

        ArrayList<Serializable> items = getItems(threadId, state);
        while (!items.isEmpty() && items.get(items.size() - 1) instanceof OpenSegment s
                && !s.inherited() && s.owner().equals(frame.id())) {

            LogSegmentStatus status = failed || s.failed() ? LogSegmentStatus.ERROR : LogSegmentStatus.OK;
            pop(runtime, state, threadId, items, status);
        }
    }

    /**
     * Closes the segments of the frame interrupted by the error. The segment guarded by
     * the frame's exception handler stays open until its handler completes. A step without
     * a segment (e.g. its name failed to evaluate) marks nothing.
     */
    public static void onExceptionHandler(Runtime runtime, State state, ThreadId threadId, Frame frame) {
        ArrayList<Serializable> items = getItems(threadId, state);
        while (!items.isEmpty() && items.get(items.size() - 1) instanceof OpenSegment s
                && !s.inherited() && s.owner().equals(frame.id()) && !s.keepOnHandler()) {

            pop(runtime, state, threadId, items, LogSegmentStatus.ERROR);
        }

        if (!items.isEmpty() && items.get(items.size() - 1) instanceof OpenSegment s
                && !s.inherited() && s.owner().equals(frame.id())) {
            items.set(items.size() - 1, new OpenSegment(s.context(), s.kind(), s.owner(), s.keepOnHandler(), false, true));
            state.setThreadLocal(threadId, KEY, items);
        }
    }

    /**
     * Makes the innermost segment of the parent thread the parent of the child's segments.
     * The child never closes or updates the inherited segment.
     */
    public static void inherit(ThreadId parentThreadId, ThreadId childThreadId, State state) {
        LogContext ctx = getLogContext(parentThreadId, state);
        if (ctx == null) {
            return;
        }

        ArrayList<Serializable> items = new ArrayList<>();
        items.add(new OpenSegment(ctx, null, null, false, true, false));
        state.setThreadLocal(childThreadId, KEY, items);
    }

    /**
     * Runs the action with the logs going into the innermost segment of the thread.
     * For commands that log outside of {@link StepCommand}, e.g. wrappers.
     */
    public static void withLogContext(Runtime runtime, State state, ThreadId threadId, Runnable action) {
        LogContext ctx = getLogContext(threadId, state);
        if (ctx == null) {
            action.run();
        } else {
            runtime.getService(RunnerLogger.class).withContext(ctx, action);
        }
    }

    /**
     * Returns the innermost segment of the thread, including the inherited one.
     */
    @Nullable
    public static LogContext getLogContext(ThreadId threadId, State state) {
        List<Serializable> items = getItems(threadId, state);
        return items.isEmpty() ? null : toContext(items.get(items.size() - 1));
    }

    /**
     * Returns the segments owned by the thread, outermost first.
     */
    public static List<LogContext> getLogContexts(ThreadId threadId, State state) {
        return getItems(threadId, state).stream()
                .filter(i -> !(i instanceof OpenSegment s && s.inherited()))
                .map(LogSegmentUtils::toContext)
                .toList();
    }

    /**
     * The segment of the step with the specified correlation ID, if the context is the step's own
     * segment (or its iteration or attempt). Steps without a segment of their own (and the system
     * segment) have none.
     */
    @Nullable
    public static Long getStepSegmentId(LogContext context, UUID correlationId) {
        if (context == null || context.segmentId() == null || context.segmentId() == SYSTEM_SEGMENT_ID) {
            return null;
        }
        return correlationId.equals(context.correlationId()) ? context.segmentId() : null;
    }

    /**
     * Used by {@link LogSegmentScopeCommand} of older processes.
     */
    public static void pushLogContext(ThreadId threadId, State state, LogContext logContext) {
        push(threadId, state, logContext);
    }

    /**
     * Used by {@link CloseLogSegmentCommand} of older processes.
     */
    @Nullable
    public static LogContext popLogContext(ThreadId threadId, State state) {
        ArrayList<Serializable> items = getItems(threadId, state);
        if (items.isEmpty()) {
            return null;
        }

        Serializable result = items.remove(items.size() - 1);
        save(threadId, state, items);
        return toContext(result);
    }

    /**
     * The innermost open segment, {@code null} if none or if it's the system segment
     * (a flat "catch-all" segment, not a real parent).
     */
    @Nullable
    public static Long getParentId(ThreadId threadId, State state) {
        LogContext ctx = getLogContext(threadId, state);
        if (ctx == null || ctx.segmentId() == null || ctx.segmentId() == SYSTEM_SEGMENT_ID) {
            return null;
        }
        return ctx.segmentId();
    }

    /**
     * The runtime thread of a segment, {@code null} for the main thread.
     */
    @Nullable
    public static Integer getThread(State state, ThreadId threadId) {
        if (threadId.equals(state.getRootThreadId()) || threadId.id() > Integer.MAX_VALUE) {
            return null;
        }
        return (int) threadId.id();
    }

    private static void pop(Runtime runtime, State state, ThreadId threadId, ArrayList<Serializable> items, LogSegmentStatus status) {
        OpenSegment s = (OpenSegment) items.remove(items.size() - 1);
        save(threadId, state, items);

        runtime.getService(RunnerLogger.class)
                .setSegmentStatus(s.context().segmentId(), status);
    }

    private static void push(ThreadId threadId, State state, Serializable item) {
        ArrayList<Serializable> items = getItems(threadId, state);
        items.add(item);
        state.setThreadLocal(threadId, KEY, items);
    }

    private static void save(ThreadId threadId, State state, ArrayList<Serializable> items) {
        if (items.isEmpty()) {
            state.removeThreadLocal(threadId, KEY);
        } else {
            state.setThreadLocal(threadId, KEY, items);
        }
    }

    private static ArrayList<Serializable> getItems(ThreadId threadId, State state) {
        ArrayList<Serializable> items = state.getThreadLocal(threadId, KEY);
        return items != null ? items : new ArrayList<>();
    }

    private static LogContext toContext(Serializable item) {
        return item instanceof OpenSegment s ? s.context() : (LogContext) item;
    }

    /**
     * @param owner     the frame the segment was opened in, {@code null} if inherited
     * @param failed    the segment failed before entering its error handler
     * @param inherited the innermost segment of the parent thread, never closed by the thread
     */
    private record OpenSegment(LogContext context,
                               @Nullable LogSegmentKind kind,
                               @Nullable FrameId owner,
                               boolean keepOnHandler,
                               boolean inherited,
                               boolean failed) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private LogSegmentUtils() {
    }
}
