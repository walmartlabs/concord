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
import java.util.function.Function;

public final class VMUtils {

    /**
     * Evaluates input using all currently available variables.
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

    @SuppressWarnings("unchecked")
    public static <T> T getLocal(State state, ThreadId threadId, String key, LookupType lookupType) {
        switch (lookupType) {
            case ONLY_CURRENT: {
                return (T) state.peekFrame(threadId).getLocal(key);
            }
            case INCLUDE_ANCESTORS: {
                List<Frame> frames = state.getFrames(threadId);
                return (T) mapLocals(frames, key, Function.identity());
            }
            default: {
                throw new IllegalArgumentException("Unsupported lookup type: " + lookupType);
            }
        }
    }

    public static Map<String, Object> getLocals(Context ctx) {
        ThreadId threadId = ctx.execution().currentThreadId();
        State state = ctx.execution().state();
        return getLocals(state, threadId);
    }

    public static Map<String, Object> getLocals(State state, ThreadId threadId) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Frame> frames = state.getFrames(threadId);
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);
            result.putAll(f.getLocals());
        }

        return Collections.unmodifiableMap(result);
    }

    public static void putLocal(Frame frame, String key, Object value) {
        if (value instanceof Serializable) {
            frame.setLocal(key, (Serializable) value);
            return;
        }

        String msg = "Can't set a non-serializable local variable: %s -> %s";
        throw new IllegalStateException(String.format(msg, key, value.getClass()));
    }

    public static void putLocals(Frame frame, Map<String, Object> locals) {
        if (locals == null || locals.isEmpty()) {
            return;
        }

        locals.forEach((k, v) -> putLocal(frame, k, v));
    }

    /**
     * Applies {@code fn} to each local variable, starting from the most recent frame.
     */
    public static <T> T mapLocals(List<Frame> frames, String key, Function<Object, T> fn) {
        for (Frame f : frames) {
            Object v = f.getLocal(key);
            if (v != null) {
                return fn.apply(v);
            }

            if (f.getType() == FrameType.ROOT) {
                break;
            }
        }

        return fn.apply(null);
    }

    public enum LookupType {

        /**
         * Check only the current frame.
         */
        ONLY_CURRENT,

        /**
         * Check the current frame and all its ancestors up to the nearest
         * {@link FrameType#ROOT} frame. If the current is a {@link FrameType#ROOT}
         * frame then act like it's a {@link #ONLY_CURRENT} lookup.
         */
        INCLUDE_ANCESTORS
    }

    private VMUtils() {
    }
}
