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

public interface Runtime {

    /**
     * Spawns a new "real" thread and runs the specified "vm" thread in it.
     */
    void spawn(State state, ThreadId threadId);

    /**
     * Runs the specified "vm" thread on current java thread.
     */
    EvalResult eval(State state, ThreadId threadId) throws Exception;

    /**
     * Returns an instance of the specified service using the underlying injector.
     */
    <T> T getService(Class<T> klass);
}
