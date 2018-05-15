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
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionContextFactory;
import io.takari.bpm.api.Variables;
import io.takari.bpm.context.DefaultExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.el.ExpressionManager;

import java.util.Map;

public class ConcordExecutionContextFactory implements ExecutionContextFactory<ConcordExecutionContextFactory.ConcordExecutionContext> {

    private final ExpressionManager expressionManager;

    public ConcordExecutionContextFactory(ExpressionManager expressionManager) {
        this.expressionManager = expressionManager;
    }

    @Override
    public ConcordExecutionContext create(Variables source) {
        return new ConcordExecutionContext(this, expressionManager, source);
    }

    @Override
    public ConcordExecutionContext create(Variables source, String processDefinitionId, String elementId) {
        return new ConcordExecutionContext(this, expressionManager, source, processDefinitionId, elementId);
    }

    @Override
    public ExecutionContext withOverrides(ExecutionContext delegate, Map<Object, Object> overrides) {
        return new MapBackedExecutionContext(delegate, overrides);
    }

    public static class ConcordExecutionContext extends ExecutionContextImpl implements Context {

        public ConcordExecutionContext(ExecutionContextFactory<? extends ExecutionContext> ctxFactory,
                                       ExpressionManager expressionManager, Variables source) {
            super(ctxFactory, expressionManager, source);
        }

        public ConcordExecutionContext(ExecutionContextFactory<? extends ExecutionContext> ctxFactory,
                                       ExpressionManager expressionManager, Variables source,
                                       String processDefinitionId, String elementId) {
            super(ctxFactory, expressionManager, source, processDefinitionId, elementId);
        }
    }

    public static class MapBackedExecutionContext extends DefaultExecutionContextFactory.MapBackedExecutionContext implements Context {

        public MapBackedExecutionContext(ExecutionContext delegate, Map<Object, Object> overrides) {
            super(delegate, overrides);
        }
    }
}
