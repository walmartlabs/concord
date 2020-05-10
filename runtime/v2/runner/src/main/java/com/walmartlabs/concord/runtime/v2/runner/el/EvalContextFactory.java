package com.walmartlabs.concord.runtime.v2.runner.el;

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


import com.walmartlabs.concord.runtime.v2.sdk.Context;

import java.util.Collections;
import java.util.Map;

public final class EvalContextFactory {

    /**
     * Includes the current global variables.
     * Allows access to tasks.
     * Allows intermediate results, e.g.
     * <pre>{@code
     * - set:
     *      name: "Concord"
     *      msg: "Hello, ${name}!" # evaluated to "Hello, Concord!"
     * }</pre>
     */
    public static EvalContext scope(Context ctx) {
        return DefaultEvalContext.builder()
                .context(ctx)
                .variables(ctx.globalVariables().toMap())
                .useIntermediateResults(true)
                .build();
    }

    /**
     * Includes the current global variables.
     * Allows access to tasks.
     * Doesn't allow access to intermediate results.
     */
    public static EvalContext global(Context ctx) {
        return DefaultEvalContext.builder()
                .context(ctx)
                .variables(ctx.globalVariables().toMap())
                .build();
    }

    /**
     * Includes only the specified variables.
     * Allows access to tasks.
     * Doesn't allow access to intermediate results.
     */
    public static EvalContext strict(Context ctx, Map<String, Object> variables) {
        return DefaultEvalContext.builder()
                .context(ctx)
                .variables(variables == null ? Collections.emptyMap() : variables)
                .build();
    }

    private EvalContextFactory() {
    }
}
