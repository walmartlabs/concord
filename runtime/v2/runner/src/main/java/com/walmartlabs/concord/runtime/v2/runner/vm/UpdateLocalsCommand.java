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
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.util.Map;

/**
 * Takes the input, interpolates its values and sets the result
 * as the current frame's local variables.
 */
public class UpdateLocalsCommand implements Command {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> input;

    public UpdateLocalsCommand(Map<String, Object> input) {
        this.input = input;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);

        Context ctx = contextFactory.create(runtime, state, threadId, null);
        Map<String, Object> m = expressionEvaluator.evalAsMap(EvalContextFactory.scope(ctx), input);

        Frame frame = state.peekFrame(threadId);
        VMUtils.putLocals(frame, m);
    }
}
