package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Provides access to the current call's environment.
 * Can be injected into task classes using {@link javax.inject.Inject} annotation.
 */
public interface Context {

    /**
     * @return absolute path to the working directory of the current process.
     */
    Path workingDirectory();

    /**
     * @return the current process ID.
     */
    UUID processInstanceId();

    // TODO parentInstanceId?

    /**
     * Returns all variables declared before the current call.
     *
     * @return current variables.
     */
    Variables variables();

    /**
     * TODO
     *
     * @return default task parameters.
     */
    Variables defaultVariables();

    /**
     * Provides access to the filesystem utilities.
     *
     * @return {@link FileService}
     */
    FileService fileService();

    /**
     * Allows running Docker containers in Concord processes.
     *
     * @return {@link DockerService}
     */
    DockerService dockerService();

    /**
     * Provides access to Concord secrets.
     *
     * @return {@link SecretService}
     */
    SecretService secretService();

    /**
     * Project-level locking.
     *
     * @return {@link LockService}
     */
    LockService lockService();

    /**
     * @return configuration parameters for accessing Concord API.
     */
    ApiConfiguration apiConfiguration();

    /**
     * @return the current process' configuration.
     */
    ProcessConfiguration processConfiguration();

    /**
     * Provides access to the low-level details of the current process.
     * <p>
     * Unstable API, subject to change.
     *
     * @return {@link Execution}
     */
    Execution execution();

    /**
     * Provides low-level access to the DSL compiler.
     * <p>
     * Unstable API, subject to change.
     *
     * @return {@link Compiler}
     */
    Compiler compiler();

    // TODO add "evaluate" method as well?

    /**
     * "Evaluates" the specified value, resolving all variables.
     * All expressions are evaluated and replaced with resulting values.
     * Accepts strings (including expressions), lists, sets, arrays and maps.
     *
     * @param <T>  the expected type of the result.
     * @param v    the expression or an object containing expressions (lists, maps, etc).
     * @param type the expected type of the result.
     * @return the result of evaluation of the specified type.
     */
    <T> T eval(Object v, Class<T> type);

    /**
     * Same as {@link #eval(Object, Class)}, but allows providing additional variables.
     *
     * @param <T>                 the expected type of the result.
     * @param v                   the expression or an object containing expressions
     *                            (lists, maps, etc).
     * @param additionalVariables a {@link Map} of additional variables that will be
     *                            made available during evaluation.
     * @param type                the expected type of the result.
     * @return the result of evaluation of the specified type.
     */
    <T> T eval(Object v, Map<String, Object> additionalVariables, Class<T> type);

    /**
     * Suspends the current execution thread.
     * After the calling this method, the process will be stopped after
     * the current command's execution is complete.
     * On resume, the process execution will continue from
     * the next planned step.
     *
     * @param eventName name of the event. Should be unique across the process. The same
     *                  name must be specified to resume the process.
     */
    void suspend(String eventName);

    /**
     * Suspends the current task execution and resumes a {@link ReentrantTask}
     * with the provided payload.
     * <p>
     * Unstable API, subject to change.
     *
     * @param eventName the name of the event on which the process will be suspended on.
     * @param payload   passed to the {@link ReentrantTask#resume(ResumeEvent)} method
     *                  once the process is resumed.
     */
    void reentrantSuspend(String eventName, Map<String, Serializable> payload);

    // TODO FormService
}
