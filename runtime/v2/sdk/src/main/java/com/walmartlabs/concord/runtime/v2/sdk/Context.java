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

import java.nio.file.Path;
import java.util.UUID;

public interface Context {

    /**
     * Provides access to global variables of the current process.
     */
    GlobalVariables globalVariables();

    /**
     * Provides process current working directory.
     */
    Path workingDirectory();

    /**
     * Process identifier.
     */
    UUID processInstanceId();

    // TODO parentInstanceId?
    // TODO move processInfo/projectInfo here?

    /**
     * Provides access to the low-level details of the current process.
     * @apiNote Beta API, subject to change.
     */
    Execution execution();

    /**
     * Provides low-level access to the DSL compiler.
     * @apiNote Beta API, subject to change.
     */
    Compiler compiler();

    // TODO add "evaluate" method as well?

    /**
     * "Interpolates" the specified value, resolving all variables.
     * All expressions are evaluated and replaced with resulting values.
     * Accepts strings (including expressions), lists, sets, arrays and maps.
     * Interpolation is recursive, all nested values with expressions
     * will be replaced with their evaluation results.
     */
    <T> T interpolate(Object v, Class<T> type);

    // TODO FormService
}
