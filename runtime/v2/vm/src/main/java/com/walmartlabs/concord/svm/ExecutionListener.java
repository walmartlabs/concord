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

/**
 * Provides a way to listen for different execution stages.
 * Each method can decide whether to continue the specific execution stage
 * or stop it by returning a {@link Result} value.
 */
public interface ExecutionListener {

    /**
     * Called before the next command in the stack is executed.
     */
    default Result beforeCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        return Result.CONTINUE;
    }

    /**
     * Called after the command in the stack was executed.
     */
    default Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        return Result.CONTINUE;
    }

    /**
     *  Called after the command execution ended with exception/error.
     */
    default Result afterCommandWithError(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd, Exception e) {
        return Result.CONTINUE;
    }

    /**
     * Called after each eval loop iteration.
     */
    default Result afterEval(Runtime runtime, VM vm, State state) {
        return Result.CONTINUE;
    }

    /**
     * Called after suspended threads are woken up.
     */
    default Result afterWakeUp(Runtime runtime, VM vm, State state) {
        return Result.CONTINUE;
    }

    /**
     * Called before the process executes its first step.
     */
    default void beforeProcessStart(Runtime runtime, State state) {
    }

    /**
     * Called before the process resumes the execution.
     */
    default void beforeProcessResume(Runtime runtime, State state) {
    }

    /**
     * Called after the process calls the last step.
     */
    default void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
    }

    /**
     * Called after process ends with error.
     */
    default void afterProcessEndsWithError(Runtime runtime, State state, Exception e) {
    }

    enum Result {
        CONTINUE,
        BREAK
    }
}
