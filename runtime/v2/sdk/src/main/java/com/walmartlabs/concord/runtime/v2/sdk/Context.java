package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public interface Context {

    /**
     * Provides process current working directory.
     */
    Path workingDirectory();

    /**
     * Process identifier.
     */
    UUID processInstanceId();

    // TODO parentInstanceId?

    Variables variables();

    /**
     * Default task variables
     */
    Variables defaultVariables();

    FileService fileService();

    DockerService dockerService();

    SecretService secretService();

    LockService lockService();

    ApiConfiguration apiConfiguration();

    ProcessConfiguration processConfiguration();

    /**
     * Provides access to the low-level details of the current process.
     *
     * @apiNote unstable API, subject to change.
     */
    Execution execution();

    /**
     * Provides low-level access to the DSL compiler.
     *
     * @apiNote unstable API, subject to change.
     */
    Compiler compiler();

    // TODO add "evaluate" method as well?

    /**
     * "Evaluates" the specified value, resolving all variables.
     * All expressions are evaluated and replaced with resulting values.
     * Accepts strings (including expressions), lists, sets, arrays and maps.
     */
    <T> T eval(Object v, Class<T> type);

    /**
     * Suspends the current execution thread.
     * After the calling this method, the process will be stopped after
     * the current command's execution is complete.
     * On resume, the process execution will continue from
     * the next planned step.
     */
    void suspend(String eventName);

    /**
     * Suspends the current task execution and resumes a {@link ReentrantTask}
     * with the provided payload.
     *
     * @param eventName the name of the event on which the process is suspended on.
     * @param state passed to the {@link ReentrantTask#resume(Map)} method
     *              once the process is resumed.
     * @apiNote unstable API, subject to change
     */
    void suspendResume(String eventName, Map<String, Serializable> state);

    // TODO FormService
}
