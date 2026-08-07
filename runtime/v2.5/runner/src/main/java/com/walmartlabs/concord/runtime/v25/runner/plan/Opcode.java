package com.walmartlabs.concord.runtime.v25.runner.plan;

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

public enum Opcode {
    LOG,
    LOG_YAML,
    TASK,
    SCRIPT,
    EXPR,
    CALL,
    SET,
    IF,
    SWITCH,
    GROUP,
    PARALLEL,
    FORM,
    CHECKPOINT,
    SUSPEND,
    THROW,
    RETURN,
    EXIT;

    public static Opcode from(String type) {
        return switch (type) {
            case "log" -> LOG;
            case "logYaml" -> LOG_YAML;
            case "task" -> TASK;
            case "script" -> SCRIPT;
            case "expr" -> EXPR;
            case "call" -> CALL;
            case "set" -> SET;
            case "if" -> IF;
            case "switch" -> SWITCH;
            case "try", "block" -> GROUP;
            case "parallel" -> PARALLEL;
            case "form" -> FORM;
            case "checkpoint" -> CHECKPOINT;
            case "suspend" -> SUSPEND;
            case "throw" -> THROW;
            case "return" -> RETURN;
            case "exit" -> EXIT;
            default -> throw new IllegalArgumentException("Unsupported step type: " + type);
        };
    }
}
