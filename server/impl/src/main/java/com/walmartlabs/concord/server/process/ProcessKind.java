package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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


public enum  ProcessKind {

    /**
     * Regular process.
     */
    DEFAULT,

    /**
     * Process running an failure-handling flow of a parent process.
     * Created when a parent process crashes, exits with an error or
     * otherwise fails.
     */
    FAILURE_HANDLER,

    /**
     * Process running a cancel-handling flow of a parent process.
     * Created when a user cancels a process.
     */
    CANCEL_HANDLER,

    /**
     * Process running a timeout-handling flow of a parent process.
     * Created when process terminated by timeout.
     */
    TIMEOUT_HANDLER
}
