package com.walmartlabs.concord.svm;

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

public enum ThreadStatus {

    /**
     * Ready for execution or is running.
     */
    READY,

    /**
     * Suspended, waiting for {@link VM#resume(State, java.util.Set)}.
     */
    SUSPENDED,

    /**
     * The thread is being terminated and is currently unwinding the stack.
     */
    UNWINDING,

    /**
     * Completed successfully.
     */
    DONE,

    /**
     * Completed unsuccessfully.
     */
    FAILED
}
