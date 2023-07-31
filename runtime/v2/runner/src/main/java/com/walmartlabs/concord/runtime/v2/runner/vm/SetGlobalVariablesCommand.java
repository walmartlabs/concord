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
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.*;

/**
 * Takes the input, interpolates its values and sets the result
 * as the current frame's local variables.
 * <p/>
 * Optionally takes a list of threads which root frames should be
 * updated with provided variables.
 */
public class SetGlobalVariablesCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> input;

    public SetGlobalVariablesCommand(Map<String, Object> input) {
        this.input = input;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        if (input == null || input.isEmpty()) {
            return;
        }

        // don't "pop" the stack, this command is a special case and evaluated separately

        // create the context explicitly as this command is evaluated outside or the regular
        // loop and doesn't inherit StepCommand
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        Context ctx = contextFactory.create(runtime, state, threadId, null);

        // allow access to arguments from arguments:
        /* e.g.
           configuration:
             arguments:
               args:
                 k1: v1
                 k2: ${context.variables().get('args.k1')}
         */

        Map<String, Serializable> checkedInput = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            if (e.getValue() instanceof Serializable || e.getValue() == null) {
                checkedInput.put(e.getKey(), (Serializable) e.getValue());
            } else {
                String msg = "Can't set a non-serializable global variable: %s -> %s";
                throw new IllegalStateException(String.format(msg, e.getKey(), e.getValue().getClass()));
            }
        }

        state.setGlobalVariables(checkedInput);

        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Map<String, Serializable> m = ee.evalAsMap(ecf.scope(ctx), checkedInput);

        state.setGlobalVariables(m);
    }
}
