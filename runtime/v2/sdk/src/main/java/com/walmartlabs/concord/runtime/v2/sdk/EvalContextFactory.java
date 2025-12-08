package com.walmartlabs.concord.runtime.v2.sdk;

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

public interface EvalContextFactory {

    /**
     * Includes all flow variables.
     * Allows access to tasks.
     * Allows intermediate results, e.g.
     * <pre>{@code
     * - set:
     *      name: "Concord"
     *      msg: "Hello, ${name}!" # evaluated to "Hello, Concord!"
     * }</pre>
     */
    EvalContext scope(Context ctx);

    /**
     * Includes all flow variables.
     * Allows access to tasks.
     * Doesn't allow access to intermediate results.
     */
    EvalContext global(Context ctx);

    EvalContext global(Context ctx, boolean resolveLazyValues);

    /**
     * Includes all flow variables and additional variables.
     * Allows access to tasks.
     * Doesn't allow access to intermediate results.
     */
    EvalContext global(Context ctx, Map<String, Object> additionalVariables);

    /**
     * Includes only the specified variables.
     * No flow variables allowed.
     * Allows access to tasks.
     * Doesn't allow access to intermediate results.
     */
    EvalContext strict(Context ctx, Map<String, Object> variables);

    /**
     * Includes only the specified variables.
     * No flow variables allowed.
     * Doesn't allow access to tasks.
     * Doesn't allow access to intermediate results.
     */
    EvalContext strict(Map<String, Object> variables);
}
