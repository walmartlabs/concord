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

import com.walmartlabs.concord.runtime.v2.model.SwitchStep;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SwitchCommand extends StepCommand<SwitchStep> {

    private static final long serialVersionUID = 1L;

    private final List<Map.Entry<String, Command>> caseCommands;
    private final Command defaultCommand;

    public SwitchCommand(SwitchStep step, List<Map.Entry<String, Command>> caseCommands, @Nullable Command defaultCommand) {
        super(step);
        this.caseCommands = caseCommands;
        this.defaultCommand = defaultCommand;
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        SwitchStep step = getStep();
        String expr = step.getExpression();

        Context ctx = runtime.getService(Context.class);
        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        EvalContext evalContext = ecf.global(ctx);

        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        String switchResult = ee.eval(evalContext, expr, String.class);
        boolean caseFound = false;
        for (Map.Entry<String, Command> kv : caseCommands) {
            String caseLabel = ee.eval(evalContext, kv.getKey(), String.class);
            if (Objects.equals(switchResult, caseLabel)) {
                frame.push(kv.getValue());
                caseFound = true;
                break;
            }
        }

        if (!caseFound && defaultCommand != null) {
            frame.push(defaultCommand);
        }

        // TODO: log case not found?
    }
}
