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

import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogScope;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentAttributes;
import com.walmartlabs.concord.runtime.v2.sdk.Constants;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.FrameType;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The per-thread stack of log scopes: the log segments of the thread and the scopes that
 * shape the attributes of the segments opened directly in them (no other segment in between).
 */
public final class LogSegmentUtils {

    private static final Logger log = LoggerFactory.getLogger(LogSegmentUtils.class);

    private static final String KEY = "logContext";

    private static final long SYSTEM_SEGMENT_ID = 0;

    /**
     * Frame variable: index of the loop iteration the frame belongs to.
     * Set by {@link LoopWrapper} on the frame of each iteration.
     */
    static final String LOOP_INDEX_KEY = "__logSegmentLoopIndex";

    public static LogContext popLogContext(ThreadId threadId, State state) {
        List<LogScope> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("Can't pop log context: empty log context");
        }

        if (!(items.get(items.size() - 1) instanceof LogContext result)) {
            throw new IllegalStateException("Can't pop log context: the innermost scope is not a log segment: " + items.get(items.size() - 1));
        }

        items.remove(items.size() - 1);
        if (items.isEmpty()) {
            state.removeThreadLocal(threadId, KEY);
        }
        return result;
    }

    /**
     * Returns the innermost log segment of the thread.
     */
    public static LogContext getLogContext(ThreadId threadId, State state) {
        List<LogScope> items = getScopes(threadId, state);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof LogContext ctx) {
                return ctx;
            }
        }
        return null;
    }

    /**
     * Returns the log segments of the thread, outermost first.
     */
    public static List<LogContext> getLogContexts(ThreadId threadId, State state) {
        return getScopes(threadId, state).stream()
                .filter(LogContext.class::isInstance)
                .map(LogContext.class::cast)
                .toList();
    }

    public static void pushLogContext(ThreadId threadId, State state, LogContext logContext) {
        pushScope(threadId, state, logContext);
    }

    /**
     * Opens a scope guarded by an {@code error} block. Segments closed in the scope until
     * the handler starts are recorded: the last failed one becomes the parent of the handler.
     *
     * @return the ID of the scope, see {@link #enterErrorHandler(ThreadId, State, UUID)}
     * and {@link #closeScope(ThreadId, State, UUID)}
     */
    public static UUID openErrorScope(ThreadId threadId, State state) {
        UUID id = UUID.randomUUID();
        pushScope(threadId, state, new ErrorScope(id, false, null));
        return id;
    }

    /**
     * Marks the start of the {@code error} block of the specified scope. A {@code null} ID
     * (a handler created before the scopes were introduced) is ignored.
     */
    public static void enterErrorHandler(ThreadId threadId, State state, UUID scopeId) {
        if (scopeId == null) {
            return;
        }

        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        int i = indexOf(items, scopeId);
        if (i < 0) {
            log.warn("enterErrorHandler ['{}'] -> scope not found", scopeId);
            return;
        }

        ErrorScope scope = (ErrorScope) items.get(i);
        items.set(i, new ErrorScope(scope.id(), true, scope.failedSegmentId()));
        state.setThreadLocal(threadId, KEY, items);
    }

    /**
     * Records a closed segment in the innermost error scope that hasn't caught an error yet.
     */
    public static void onSegmentClosed(ThreadId threadId, State state, long segmentId, boolean failed) {
        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return;
        }

        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof ErrorScope scope && !scope.handling()) {
                // the system segment is shared by the steps, the failed step is unknown
                Long failedSegmentId = failed && segmentId != SYSTEM_SEGMENT_ID ? segmentId : null;
                items.set(i, new ErrorScope(scope.id(), false, failedSegmentId));
                state.setThreadLocal(threadId, KEY, items);
                return;
            }
        }
    }

    /**
     * Removes the specified scope. Doesn't throw: it runs in {@code finally} handlers,
     * possibly while the stack is unwinding.
     */
    public static void closeScope(ThreadId threadId, State state, UUID scopeId) {
        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        int i = indexOf(items, scopeId);
        if (i < 0) {
            log.warn("closeScope ['{}'] -> scope not found", scopeId);
            return;
        }

        if (i != items.size() - 1) {
            log.warn("closeScope ['{}'] -> not the innermost scope: {}", scopeId, items.subList(i + 1, items.size()));
        }

        items.remove(i);
        if (items.isEmpty()) {
            state.removeThreadLocal(threadId, KEY);
        } else {
            state.setThreadLocal(threadId, KEY, items);
        }
    }

    /**
     * Makes the segments opened directly in the child thread belong where a segment
     * opened in the parent thread would.
     */
    public static void inheritScope(ThreadId parentThreadId, ThreadId childThreadId, State state) {
        Placement placement = getPlacement(parentThreadId, state);
        if (placement.parentId() == null && !placement.errorHandler()) {
            return;
        }

        ArrayList<LogScope> items = new ArrayList<>();
        items.add(new InheritedScope(placement.parentId(), placement.errorHandler()));
        state.setThreadLocal(childThreadId, KEY, items);
    }

    /**
     * Copies log segment markers from the current flow scope into a new frame.
     * The nearest root is a scope boundary and is included in the search.
     */
    public static void copyScopedSegmentMarkers(State state, ThreadId threadId, Frame target) {
        for (Frame source : state.getFrames(threadId)) {
            if (source.hasLocal(LOOP_INDEX_KEY)) {
                target.setLocal(LOOP_INDEX_KEY, source.getLocal(LOOP_INDEX_KEY));
                return;
            }
            if (source.getType() == FrameType.ROOT) {
                return;
            }
        }
    }

    static boolean isSegmentMarker(String key) {
        return LOOP_INDEX_KEY.equals(key);
    }

    /**
     * Resolves where a new segment belongs: the enclosing segment, the retry attempt, the loop iteration,
     * the error handler and the thread.
     * <p>
     * The parent and the error handler role come from the log scope stack. The attempt and the loop index
     * are read only from specific frames, so they don't leak into nested flow calls (those have frames
     * of their own) or into commands that merely copied the variables:
     * <ul>
     *     <li>the top frame is a {@link RetryWrapper} frame: the attempt number, the retried
     *     command belongs to the frame below it;</li>
     *     <li>the frame of the command: the loop iteration.</li>
     * </ul>
     */
    public static LogSegmentAttributes getSegmentAttributes(ThreadId threadId, State state) {
        List<Frame> frames = state.getFrames(threadId);
        if (frames.isEmpty()) {
            return LogSegmentAttributes.NONE;
        }

        // frames.get(0) is the top of the stack
        Frame frame = frames.get(0);

        Integer attempt = null;
        if (frame.getType() == FrameType.NON_ROOT && frame.hasLocal(RetryWrapper.RETRY_CFG)) {
            if (frame.getLocal(Constants.Runtime.RETRY_ATTEMPT_NUMBER) instanceof Integer n) {
                attempt = n + 1;
            }
            if (frames.size() > 1) {
                frame = frames.get(1);
            }
        }

        Integer loopIndex = frame.getLocal(LOOP_INDEX_KEY) instanceof Integer i ? i : null;

        Placement placement = getPlacement(threadId, state);

        Integer thread = null;
        if (!threadId.equals(state.getRootThreadId()) && threadId.id() <= Integer.MAX_VALUE) {
            thread = (int) threadId.id();
        }

        return new LogSegmentAttributes(placement.parentId(), attempt, loopIndex, placement.errorHandler(), thread);
    }

    /**
     * The segment of the step with the specified correlation ID, if the context is the step's own
     * segment. Steps without a segment of their own (and the system segment) have none.
     */
    public static Long getStepSegmentId(LogContext context, UUID correlationId) {
        if (context == null || context.segmentId() == null || context.segmentId() == 0) {
            return null;
        }
        return correlationId.equals(context.correlationId()) ? context.segmentId() : null;
    }

    /**
     * The parent is the innermost segment, unless an error handler runs directly in it:
     * then it's the failed segment of the handler (if known).
     */
    private static Placement getPlacement(ThreadId threadId, State state) {
        List<LogScope> items = getScopes(threadId, state);

        ErrorScope handler = null;
        for (int i = items.size() - 1; i >= 0; i--) {
            LogScope scope = items.get(i);
            if (scope instanceof ErrorScope s && s.handling()) {
                if (handler == null) {
                    handler = s;
                }
            } else if (scope instanceof LogContext ctx && ctx.segmentId() != null) {
                return toPlacement(handler, ctx.segmentId(), false);
            } else if (scope instanceof InheritedScope s) {
                return toPlacement(handler, s.parentId(), s.errorHandler());
            }
        }

        return toPlacement(handler, null, false);
    }

    private static Placement toPlacement(ErrorScope handler, Long enclosingSegmentId, boolean errorHandler) {
        if (handler == null) {
            return new Placement(enclosingSegmentId, errorHandler);
        }

        Long parentId = handler.failedSegmentId() != null ? handler.failedSegmentId() : enclosingSegmentId;
        return new Placement(parentId, true);
    }

    private static void pushScope(ThreadId threadId, State state, LogScope scope) {
        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(scope);
        state.setThreadLocal(threadId, KEY, items);
    }

    private static int indexOf(List<LogScope> items, UUID scopeId) {
        if (items == null) {
            return -1;
        }

        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof ErrorScope s && s.id().equals(scopeId)) {
                return i;
            }
        }
        return -1;
    }

    private static List<LogScope> getScopes(ThreadId threadId, State state) {
        List<LogScope> items = state.getThreadLocal(threadId, KEY);
        return items != null ? items : List.of();
    }

    private record Placement(Long parentId, boolean errorHandler) {
    }

    /**
     * A scope guarded by an {@code error} block.
     *
     * @param handling        {@code true} once the {@code error} block runs
     * @param failedSegmentId the segment that failed last in the scope, {@code null} if unknown
     *                        or the last closed segment didn't fail
     */
    private record ErrorScope(UUID id, boolean handling, Long failedSegmentId) implements LogScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * The bottom of the stack of a forked thread: where the segments of the thread belong.
     */
    private record InheritedScope(Long parentId, boolean errorHandler) implements LogScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private LogSegmentUtils() {
    }
}
