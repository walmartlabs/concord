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

import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VMUtils {

    private static final String FRAME_LOCAL_OVERRIDES_KEY = "__frame_local_overrides";
    private static final String FRAME_TASKINPUT_OVERRIDES_KEY = "__frame_taskInput_overrides";

    /**
     * Puts the specified value into the frame-local overrides of global variables.
     * Should not be called concurrently from different threads for the same {@code threadId}.
     * Ideally all frame-local variables should be {@link java.io.Serializable}, but
     * we are not restricting it here.
     */
    public static void putLocalOverride(Frame frame, String key, Object value) {
        ensureLocalOverrides(frame).put(key, value);
    }

    /**
     * Puts the specified values into the frame-local overrides of global variables.
     *
     * @see #putLocalOverride(Frame, String, Object)
     */
    public static void putLocalOverrides(Frame frame, Map<String, Object> values) {
        ensureLocalOverrides(frame).putAll(values);
    }

    /**
     * Returns a frame-local override.
     * Should not be called concurrently from different threads for the same {@code threadId}.
     */
    @SuppressWarnings("unchecked")
    public static Object getLocalOverride(Frame frame, String key) {
        Map<String, Object> m = (Map<String, Object>) frame.getLocal(FRAME_LOCAL_OVERRIDES_KEY);
        if (m == null) {
            return null;
        }
        return m.get(key);
    }

    /**
     * Returns an unmodifiable map of frame-local overrides for the specified frame.
     * Collects all overrides from all frames in the thread. If the same key is defined in multiple threads
     * the most recent value is added.
     * Should not be called concurrently from different threads for the same {@code threadId}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getLocalOverrides(State state, ThreadId threadId) {
        Map<String, Object> result = new HashMap<>();

        // collect the values, starting from the "oldest" frame
        List<Frame> frames = state.getFrames(threadId);
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);

            Map<String, Object> m = (Map<String, Object>) f.getLocal(FRAME_LOCAL_OVERRIDES_KEY);
            if (m != null) {
                result.putAll(m);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    public static void setTaskInputOverrides(Frame frame, Map<String, Object> overrides) {
        // use HashMap because it is Serializable
        HashMap<String, Object> m = new HashMap<>(overrides);
        frame.setLocal(FRAME_TASKINPUT_OVERRIDES_KEY, m);
    }

    public static Map<String, Object> getTaskInputOverrides(Context ctx) {
        Execution execution = ctx.execution();
        State state = execution.state();
        Frame frame = state.peekFrame(execution.currentThreadId());
        return getTaskInputOverrides(frame);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getTaskInputOverrides(Frame frame) {
        Map<String, Object> m = (Map<String, Object>) frame.getLocal(FRAME_TASKINPUT_OVERRIDES_KEY);
        if (m == null) {
            return Collections.emptyMap();
        }
        return m;
    }

    /**
     * Combines the step input and the frame-local task input overrides.
     * I.e. {@code retry} or a similar mechanism can produce an updated
     * set of {@code in} variables which should override the original
     * {@code input}.
     */
    public static Map<String, Object> prepareInput(ExpressionEvaluator ee,
                                                   Context ctx,
                                                   Map<String, Serializable> input) {

        if (input == null) {
            input = Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>(input);

        Map<String, Object> frameOverrides = VMUtils.getTaskInputOverrides(ctx);
        result.putAll(frameOverrides);

        return Collections.unmodifiableMap(ee.evalAsMap(ctx, result));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureLocalOverrides(Frame frame) {
        // use HashMap because it is Serializable
        HashMap<String, Object> m = (HashMap<String, Object>) frame.getLocal(FRAME_LOCAL_OVERRIDES_KEY);
        if (m == null) {
            m = new HashMap<>();
            frame.setLocal(FRAME_LOCAL_OVERRIDES_KEY, m);
        }
        return m;
    }

    private VMUtils() {
    }
}
