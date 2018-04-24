package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.context.ExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.state.Variables;

public class ConcordExecutionContextFactory implements ExecutionContextFactory<ConcordExecutionContextFactory.ConcordExecutionContext> {

    private final ExpressionManager expressionManager;

    public ConcordExecutionContextFactory(ExpressionManager expressionManager) {
        this.expressionManager = expressionManager;
    }

    @Override
    public ConcordExecutionContext create(Variables source) {
        return new ConcordExecutionContext(expressionManager, source);
    }

    @Override
    public ConcordExecutionContext create(Variables source, String processDefinitionId, String elementId) {
        return new ConcordExecutionContext(expressionManager, source, processDefinitionId, elementId);
    }

    public static class ConcordExecutionContext extends ExecutionContextImpl implements Context {

        public ConcordExecutionContext(ExpressionManager expressionManager, Variables source) {
            super(expressionManager, source);
        }

        public ConcordExecutionContext(ExpressionManager expressionManager, Variables source,
                                       String processDefinitionId, String elementId) {
            super(expressionManager, source, processDefinitionId, elementId);
        }
    }
}
