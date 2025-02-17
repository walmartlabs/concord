package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

/**
 * Backward compatibility for processing old {@link State} instances.
 */
public class StateBackwardCompatibility {

    // 2.21.1-SNAPSHOT
    public static ThreadError processThreadError(Object threadError, ThreadId threadId) {
        if (threadError == null) {
            return null;
        }

        if (threadError instanceof Exception e) {
            return new ThreadError(threadId, null, e);
        }

        return (ThreadError) threadError;
    }
}
