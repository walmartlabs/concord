package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;

public final class ContextUtils {

    /**
     * Returns the current "retry" attempt number (if applicable).
     *
     * @param ctx current {@link Context}
     * @return the current attemp number of {@code null} if the current call is a retry.
     * @see Constants.Runtime#RETRY_ATTEMPT_NUMBER
     */
    public static Integer getCurrentRetryAttemptNumber(Context ctx) {
        Execution execution = ctx.execution();

        State state = execution.state();
        ThreadId threadId = execution.currentThreadId();
        Frame frame = state.peekFrame(threadId);

        Serializable value = frame.getLocal(Constants.Runtime.RETRY_ATTEMPT_NUMBER);
        if (value == null) {
            return null;
        }

        if (!(value instanceof Integer)) {
            throw new IllegalStateException(String.format("%s is expected to be a number, got: %s. This is most likely a bug",
                    Constants.Runtime.RETRY_ATTEMPT_NUMBER, value.getClass()));
        }

        return (Integer) value;
    }

    private ContextUtils() {
    }
}
