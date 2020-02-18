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

import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.model.FlowCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.Interpolator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FlowCallCommand extends StepCommand<FlowCall> {

    private static final long serialVersionUID = 1L;

    private final Command cmd;

    public FlowCallCommand(FlowCall step, Command cmd) {
        super(step);
        this.cmd = cmd;
    }

    @Override
    public void eval(Runtime runtime, State state, ThreadId threadId) {
        state.peekFrame(threadId).pop();

        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId);

        FlowCall call = getStep();
        FlowCallOptions opts = call.getOptions();
        Map<String, Object> input = prepareInput(expressionEvaluator, ctx, opts);

        Frame inner = new Frame(cmd);
        VMUtils.putLocalOverrides(inner, input);

        state.pushFrame(threadId, inner);
    }

    // TODO shared code with TaskCallCommand

    /**
     * Combines the task input and the frame-local task input overrides.
     * I.e. {@code retry} or a similar mechanism can produce an updated
     * set of {@code in} variables which should override the original
     * {@code input}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> prepareInput(ExpressionEvaluator ee,
                                                    Context ctx,
                                                    FlowCallOptions opts) {

        Map<String, Serializable> input = opts.input();
        if (input == null) {
            input = Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>(input);

        Map<String, Object> frameOverrides = VMUtils.getTaskInputOverrides(ctx);
        result.putAll(frameOverrides);

        result = new HashMap<String, Object>(Interpolator.interpolate(ee, ctx, result, Map.class));
        return Collections.unmodifiableMap(result);
    }
}
