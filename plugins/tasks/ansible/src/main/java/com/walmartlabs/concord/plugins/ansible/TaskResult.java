package com.walmartlabs.concord.plugins.ansible;

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

import java.util.Map;

public class TaskResult {

    private final boolean success;

    private final Map<String, Object> result;

    private final int exitCode;

    public TaskResult(boolean success, Map<String, Object> result, int exitCode) {
        this.success = success;
        this.result = result;
        this.exitCode = exitCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public int getExitCode() {
        return exitCode;
    }
}
