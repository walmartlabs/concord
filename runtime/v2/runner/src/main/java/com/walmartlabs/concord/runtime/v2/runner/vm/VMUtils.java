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

import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.FrameType;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VMUtils {

    /**
     * Evaluates a step's {@code in} section using all currently available variables.
     */
    public static Map<String, Object> prepareInput(ExpressionEvaluator ee,
                                                   Context ctx,
                                                   Map<String, Serializable> input) {

        if (input == null) {
            return Collections.emptyMap();
        }

        input = ee.evalAsMap(EvalContextFactory.global(ctx), input);

        return Collections.unmodifiableMap(input);
    }

    /**
     * Returns a local variable {@code key} or throws an {@link IllegalStateException}
     * if the variable doesn't exist or {@code null}.
     * <p/>
     * Only the current frame is considered.
     */
    public static <T> T assertLocal(State state, ThreadId threadId, String key) {
        T v = getLocal(state, threadId, key);
        if (v == null) {
            throw new IllegalStateException("Variable doesn't exist or has a null value: " + key);
        }
        return v;
    }

    /**
     * Returns a local variable {@code key} or {@code null} if the variable
     * doesn't exist or {@code null}.
     * <p/>
     * Only the current frame is considered.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getLocal(State state, ThreadId threadId, String key) {
        Serializable v = state.peekFrame(threadId).getLocal(key);
        if (v == null) {
            return null;
        }

        return (T) v;
    }

    /**
     * Returns a local variable {@code key} or {@code null} if the variable
     * doesn't exist or {@code null}.
     * <p/>
     * The method scans all frames starting from the most recent one and returns
     * the first found element.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getCombinedLocal(State state, ThreadId threadId, String key) {
        List<Frame> frames = state.getFrames(threadId);

        for (Frame f : frames) {
            if (f.hasLocal(key)) {
                return (T) f.getLocal(key);
            }
        }

        return null;
    }

    /**
     * Returns {@code} true if a local variable {@code key} exists.
     * <p/>
     * The method scans all frames starting from the most recent one.
     */
    public static boolean hasCombinedLocal(State state, ThreadId threadId, String key) {
        List<Frame> frames = state.getFrames(threadId);

        for (Frame f : frames) {
            if (f.hasLocal(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @see #getCombinedLocals(State, ThreadId)
     */
    public static Map<String, Object> getCombinedLocals(Context ctx) {
        ThreadId threadId = ctx.execution().currentThreadId();
        State state = ctx.execution().state();
        return getCombinedLocals(state, threadId);
    }

    /**
     * Returns a map of all variables combined, starting from the bottom of the stack.
     */
    public static Map<String, Object> getCombinedLocals(State state, ThreadId threadId) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Frame> frames = state.getFrames(threadId);
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);
            result.putAll(f.getLocals());
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Puts a local variable into the nearest root frame of the specified thread.
     * Only {@link Serializable} values are allowed.
     */
    public static void putLocal(State state, ThreadId threadId, String key, Object value) {
        Frame root = assertNearestRoot(state, threadId);
        putLocal(root, key, value);
    }

    /**
     * Puts a local variable into the specified frame.
     * Only {@link Serializable} values are allowed.
     */
    public static void putLocal(Frame frame, String key, Object value) {
        if (value instanceof Serializable || value == null) {
            frame.setLocal(key, (Serializable) value);
            return;
        }

        String msg = "Can't set a non-serializable local variable: %s -> %s";
        throw new IllegalStateException(String.format(msg, key, value.getClass()));
    }

    /**
     * Puts all key-value pairs into the specified frame as locals.
     * Only {@link Serializable} values are allowed.
     *
     * @see #putLocal(Frame, String, Object)
     */
    public static void putLocals(Frame frame, Map<String, Object> locals) {
        if (locals == null || locals.isEmpty()) {
            return;
        }

        locals.forEach((k, v) -> putLocal(frame, k, v));
    }

    public static Frame assertNearestRoot(State state, ThreadId threadId) {
        List<Frame> frames = state.getFrames(threadId);

        for (Frame f : frames) {
            if (f.getType() == FrameType.ROOT) {
                return f;
            }
        }

        throw new IllegalStateException("Can't find a nearest ROOT frame. This is most likely a bug.");
    }

    private VMUtils() {
    }
}
