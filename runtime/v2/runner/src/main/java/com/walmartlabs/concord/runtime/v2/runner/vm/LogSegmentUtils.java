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
        if (items == null) {
            // no scopes: nothing was opened or the state doesn't support thread locals
            return;
        }

        int i = indexOf(items, scopeId);
        if (i < 0 || !(items.get(i) instanceof ErrorScope scope)) {
            log.warn("enterErrorHandler ['{}'] -> scope not found", scopeId);
            return;
        }

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
     * Opens the scope of a loop iteration.
     */
    public static UUID openLoopScope(ThreadId threadId, State state, int index) {
        UUID id = UUID.randomUUID();
        pushScope(threadId, state, new LoopScope(id, index));
        return id;
    }

    /**
     * Opens the scope of a step with {@code retry}.
     *
     * @param attempt the number of the attempt, starting from 1
     */
    public static UUID openAttemptScope(ThreadId threadId, State state, int attempt) {
        UUID id = UUID.randomUUID();
        pushScope(threadId, state, new AttemptScope(id, attempt));
        return id;
    }

    /**
     * Updates the attempt number of the specified scope. A {@code null} ID
     * (a retry started before the scopes were introduced) is ignored.
     */
    public static void setAttempt(ThreadId threadId, State state, UUID scopeId, int attempt) {
        if (scopeId == null) {
            return;
        }

        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            // no scopes: nothing was opened or the state doesn't support thread locals
            return;
        }

        int i = indexOf(items, scopeId);
        if (i < 0) {
            log.warn("setAttempt ['{}'] -> scope not found", scopeId);
            return;
        }

        items.set(i, new AttemptScope(scopeId, attempt));
        state.setThreadLocal(threadId, KEY, items);
    }

    /**
     * Removes the specified scope. Doesn't throw: it runs in {@code finally} handlers,
     * possibly while the stack is unwinding.
     */
    public static void closeScope(ThreadId threadId, State state, UUID scopeId) {
        ArrayList<LogScope> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            // no scopes: nothing was opened or the state doesn't support thread locals
            return;
        }

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
        if (placement.equals(Placement.NONE)) {
            return;
        }

        ArrayList<LogScope> items = new ArrayList<>();
        items.add(new InheritedScope(placement.parentId(), placement.errorHandler(), placement.loopIndex(), placement.attempt()));
        state.setThreadLocal(childThreadId, KEY, items);
    }

    /**
     * Resolves where a new segment belongs: the enclosing segment, the retry attempt, the loop iteration,
     * the error handler and the thread.
     */
    public static LogSegmentAttributes getSegmentAttributes(ThreadId threadId, State state) {
        Placement placement = getPlacement(threadId, state);

        Integer thread = null;
        if (!threadId.equals(state.getRootThreadId()) && threadId.id() <= Integer.MAX_VALUE) {
            thread = (int) threadId.id();
        }

        return new LogSegmentAttributes(placement.parentId(), placement.attempt(), placement.loopIndex(), placement.errorHandler(), thread);
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
     * then it's the failed segment of the handler (if known). The innermost scopes
     * opened directly in the segment provide the loop index and the attempt.
     */
    private static Placement getPlacement(ThreadId threadId, State state) {
        List<LogScope> items = getScopes(threadId, state);

        ErrorScope handler = null;
        Integer loopIndex = null;
        Integer attempt = null;
        for (int i = items.size() - 1; i >= 0; i--) {
            LogScope scope = items.get(i);
            if (scope instanceof ErrorScope s && s.handling()) {
                if (handler == null) {
                    handler = s;
                }
            } else if (scope instanceof LoopScope s) {
                if (loopIndex == null) {
                    loopIndex = s.index();
                }
            } else if (scope instanceof AttemptScope s) {
                if (attempt == null) {
                    attempt = s.attempt();
                }
            } else if (scope instanceof LogContext ctx && ctx.segmentId() != null) {
                return toPlacement(handler, ctx.segmentId(), false, loopIndex, attempt);
            } else if (scope instanceof InheritedScope s) {
                return toPlacement(handler, s.parentId(), s.errorHandler(),
                        loopIndex != null ? loopIndex : s.loopIndex(),
                        attempt != null ? attempt : s.attempt());
            }
        }

        return toPlacement(handler, null, false, loopIndex, attempt);
    }

    private static Placement toPlacement(ErrorScope handler, Long enclosingSegmentId, boolean errorHandler, Integer loopIndex, Integer attempt) {
        if (handler == null) {
            return new Placement(enclosingSegmentId, errorHandler, loopIndex, attempt);
        }

        Long parentId = handler.failedSegmentId() != null ? handler.failedSegmentId() : enclosingSegmentId;
        return new Placement(parentId, true, loopIndex, attempt);
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
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof IdentifiedScope s && s.id().equals(scopeId)) {
                return i;
            }
        }
        return -1;
    }

    private static List<LogScope> getScopes(ThreadId threadId, State state) {
        List<LogScope> items = state.getThreadLocal(threadId, KEY);
        return items != null ? items : List.of();
    }

    private record Placement(Long parentId, boolean errorHandler, Integer loopIndex, Integer attempt) {

        static final Placement NONE = new Placement(null, false, null, null);
    }

    /**
     * A scope closed by its ID, see {@link #closeScope(ThreadId, State, UUID)}.
     */
    private interface IdentifiedScope extends LogScope {

        UUID id();
    }

    /**
     * A scope guarded by an {@code error} block.
     *
     * @param handling        {@code true} once the {@code error} block runs
     * @param failedSegmentId the segment that failed last in the scope, {@code null} if unknown
     *                        or the last closed segment didn't fail
     */
    private record ErrorScope(UUID id, boolean handling, Long failedSegmentId) implements IdentifiedScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * The bottom of the stack of a forked thread: where the segments of the thread belong.
     */
    private record InheritedScope(Long parentId, boolean errorHandler, Integer loopIndex, Integer attempt) implements LogScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * An iteration of {@code loop} or {@code withItems}.
     */
    private record LoopScope(UUID id, int index) implements IdentifiedScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * An attempt (starting from 1) of a step with {@code retry}.
     */
    private record AttemptScope(UUID id, int attempt) implements IdentifiedScope {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private LogSegmentUtils() {
    }
}
