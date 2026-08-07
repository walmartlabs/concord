package com.walmartlabs.concord.runtime.v25.runner.engine;

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

import com.walmartlabs.concord.runtime.v25.model.Values;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record ProcessResult(ProcessStatus status, Map<String, Object> variables, Map<String, Object> outputs,
                            Failure failure, Suspension suspension) implements Serializable {

    public ProcessResult {
        variables = Values.map(variables);
        outputs = Values.map(outputs);
    }

    public static ProcessResult succeeded(Map<String, Object> variables, Map<String, Object> outputs) {
        return new ProcessResult(ProcessStatus.SUCCEEDED, variables, outputs, null, null);
    }

    public static ProcessResult failed(Map<String, Object> variables, Failure failure) {
        return new ProcessResult(ProcessStatus.FAILED, variables, Map.of(), failure, null);
    }

    public static ProcessResult suspended(Map<String, Object> variables, Suspension suspension) {
        return new ProcessResult(ProcessStatus.SUSPENDED, variables, Map.of(), null, suspension);
    }

    public static ProcessResult cancelled(Map<String, Object> variables, Failure failure) {
        return new ProcessResult(ProcessStatus.CANCELLED, variables, Map.of(), failure, null);
    }

    public record Failure(String code, String message, String source, int line, int column,
                          String path, List<String> callStack, Integer parallelBranchIndex,
                          Integer loopItemIndex, Integer retryAttempt, Throwable cause) implements Serializable {

        public Failure {
            callStack = List.copyOf(callStack);
        }
    }
}
