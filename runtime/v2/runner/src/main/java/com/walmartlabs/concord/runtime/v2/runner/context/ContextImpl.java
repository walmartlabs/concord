package com.walmartlabs.concord.runtime.v2.runner.context;

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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.Interpolator;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Execution;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

public class ContextImpl implements Context {

    private final GlobalVariables globalVariables;
    private final ThreadId currentThreadId;
    private final Runtime runtime;
    private final State state;
    private final ProcessDefinition processDefinition;
    private final Compiler compiler;
    private final ExpressionEvaluator expressionEvaluator;

    public ContextImpl(GlobalVariables globalVariables,
                       ThreadId currentThreadId,
                       Runtime runtime,
                       State state,
                       ProcessDefinition processDefinition,
                       Compiler compiler,
                       ExpressionEvaluator expressionEvaluator) {

        this.globalVariables = globalVariables;
        this.currentThreadId = currentThreadId;
        this.runtime = runtime;
        this.state = state;
        this.processDefinition = processDefinition;
        this.compiler = compiler;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public GlobalVariables globalVariables() {
        return globalVariables;
    }

    @Override
    public Execution execution() {
        return new Execution() {
            @Override
            public ThreadId currentThreadId() {
                return currentThreadId;
            }

            @Override
            public Runtime runtime() {
                return runtime;
            }

            @Override
            public State state() {
                return state;
            }

            @Override
            public ProcessDefinition processDefinition() {
                return processDefinition;
            }
        };
    }

    @Override
    public Compiler compiler() {
        return compiler;
    }

    @Override
    public <T> T interpolate(Object v, Class<T> type) {
        return Interpolator.interpolate(expressionEvaluator, this, v, type);
    }
}
