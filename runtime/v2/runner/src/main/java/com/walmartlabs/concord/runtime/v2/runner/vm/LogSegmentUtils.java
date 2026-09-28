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
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentAttributes;
import com.walmartlabs.concord.runtime.v2.sdk.Constants;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.FrameType;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LogSegmentUtils {

    private static final String KEY = "logContext";
    private static final String PARENT_SEGMENT_ID_KEY = "parentLogSegmentId";
    private static final String LAST_FAILED_SEGMENT_ID_KEY = "lastFailedLogSegmentId";

    /**
     * Frame variable: index of the loop iteration the frame belongs to.
     * Set by {@link LoopWrapper} on the frame of each iteration.
     */
    static final String LOOP_INDEX_KEY = "__logSegmentLoopIndex";

    /**
     * Frame variable: ID of the failed segment ({@code -1} if unknown) whose {@code error} block
     * runs in the frame. Set by {@link ExposeLastErrorCommand}.
     */
    static final String ERROR_HANDLER_KEY = "__logSegmentErrorHandlerFor";

    public static LogContext popLogContext(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("Can't pop log context: empty log context");
        }

        LogContext result = items.remove(items.size() - 1);
        if (items.isEmpty()) {
            state.removeThreadLocal(threadId, KEY);
        }
        return result;
    }

    public static LogContext getLogContext(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("Can't get log context: empty log context");
        }

        return items.get(items.size() - 1);
    }

    public static List<LogContext> getLogContexts(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return List.of();
        }

        return items;
    }

    public static void pushLogContext(ThreadId threadId, State state, LogContext logContext) {
        ArrayList<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(logContext);
        state.setThreadLocal(threadId, KEY, items);
    }

    /**
     * Returns the ID of the innermost log segment of the thread. If the thread
     * has no segments of its own, returns the segment inherited from the parent
     * thread (see {@link #inheritParentSegmentId(ThreadId, ThreadId, State)}).
     */
    public static Long getCurrentSegmentId(ThreadId threadId, State state) {
        List<LogContext> items = getLogContexts(threadId, state);
        for (int i = items.size() - 1; i >= 0; i--) {
            LogContext ctx = items.get(i);
            if (ctx != null && ctx.segmentId() != null) {
                return ctx.segmentId();
            }
        }
        return state.getThreadLocal(threadId, PARENT_SEGMENT_ID_KEY);
    }

    /**
     * Makes the current segment of the parent thread the parent segment
     * for all segments created in the child thread.
     */
    public static void inheritParentSegmentId(ThreadId parentThreadId, ThreadId childThreadId, State state) {
        Long segmentId = getCurrentSegmentId(parentThreadId, state);
        if (segmentId != null) {
            state.setThreadLocal(childThreadId, PARENT_SEGMENT_ID_KEY, segmentId);
        }
    }

    /**
     * Resolves where a new segment belongs: the enclosing segment, the retry attempt, the loop iteration,
     * the error handler and the thread.
     * <p>
     * The markers are read only from specific frames, so they don't leak into nested flow calls
     * (those have frames of their own) or into commands that merely copied the variables:
     * <ul>
     *     <li>the top frame is a {@link RetryWrapper} frame: the attempt number, the retried
     *     command belongs to the frame below it;</li>
     *     <li>the frame of the command: the loop iteration and the error handler markers.</li>
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

        Long parentId = getCurrentSegmentId(threadId, state);

        boolean errorHandler = false;
        Serializable failedSegmentId = frame.getLocal(ERROR_HANDLER_KEY);
        if (failedSegmentId instanceof Long id) {
            errorHandler = true;
            // error handlers are shown as children of the failed step
            if (id >= 0) {
                parentId = id;
            }
        }

        Integer thread = null;
        if (!threadId.equals(state.getRootThreadId()) && threadId.id() <= Integer.MAX_VALUE) {
            thread = (int) threadId.id();
        }

        return new LogSegmentAttributes(parentId, attempt, loopIndex, errorHandler, thread);
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

    public static void setLastFailedSegmentId(ThreadId threadId, State state, long segmentId) {
        state.setThreadLocal(threadId, LAST_FAILED_SEGMENT_ID_KEY, segmentId);
    }

    public static Long getLastFailedSegmentId(ThreadId threadId, State state) {
        return state.getThreadLocal(threadId, LAST_FAILED_SEGMENT_ID_KEY);
    }

    private LogSegmentUtils() {
    }
}
