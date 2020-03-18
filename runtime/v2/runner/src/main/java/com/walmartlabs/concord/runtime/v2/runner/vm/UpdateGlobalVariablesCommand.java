package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.Interpolator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.util.Map;

/**
 * Takes {@link #input}, interpolates its keys and values and puts the result
 * into the current {@link GlobalVariables} instance.
 * <br/><br/>
 * This command solves a "chicken-and-egg" problem of setting the initial
 * global variables (when the process starts) and updating them (when the
 * process receives a resume event). In order to evaluate expressions in
 * the input we need the runtime and we can't build the runtime until we get an
 * instance of {@link GlobalVariables}. So the solution is to create an empty
 * {@link GlobalVariables} and populate them by running this command before
 * all other commands. It is important to run this command before any other
 * commands, especially in cases when the {@code State} contains multiple
 * threads &mdash; all threads must "see" the same {@link GlobalVariables}
 * values simultaneously.
 */
public class UpdateGlobalVariablesCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> input;

    public UpdateGlobalVariablesCommand(Map<String, Object> input) {
        this.input = input;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);

        Context ctx = contextFactory.create(runtime, state, threadId, null);
        Map<String, Object> m = Interpolator.interpolate(expressionEvaluator, ctx, input, Map.class);

        GlobalVariables globalVariables = runtime.getService(GlobalVariables.class);
        globalVariables.putAll(m);
    }
}
