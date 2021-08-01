package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import java.io.Serializable;
import java.util.List;

public final class Utils {

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
     * Returns the parent frame of the current frame of the specified thread.
     */
    public static Frame getParentFrame(State state, ThreadId threadId) {
        // we assume that the parent frame is the frame before the current one in the stack
        List<Frame> frames = state.getFrames(threadId);
        if (frames.size() < 2) {
            return null;
        }
        return frames.get(1);
    }

    private Utils() {
    }
}
