package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

final class CliExitCodes {

    static final int SUCCESS = 0;
    static final int ERROR = 1;
    static final int USAGE = 2;
    static final int SUSPENDED = 20;
    static final int INPUT_REQUIRED = 21;
    static final int NON_INTERACTIVE_UNSUPPORTED = 22;
    static final int PROCESS_FAILED = -1;

    private CliExitCodes() {
    }
}
