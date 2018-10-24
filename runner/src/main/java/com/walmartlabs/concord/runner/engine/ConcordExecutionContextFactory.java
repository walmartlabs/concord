package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionContextFactory;
import io.takari.bpm.api.Variables;
import io.takari.bpm.context.DefaultExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.el.ExpressionManager;

import java.util.*;

public class ConcordExecutionContextFactory implements ExecutionContextFactory<ConcordExecutionContextFactory.ConcordExecutionContext> {

    private final ExpressionManager expressionManager;
    private final ProtectedVarContext protectedVarContext;

    public ConcordExecutionContextFactory(ExpressionManager expressionManager, ProtectedVarContext protectedVarContext) {
        this.expressionManager = expressionManager;
        this.protectedVarContext = protectedVarContext;
    }

    @Override
    public ConcordExecutionContext create(Variables source) {
        return new ConcordExecutionContext(this, expressionManager, source, protectedVarContext);
    }

    @Override
    public ConcordExecutionContext create(Variables source, String processDefinitionId, String elementId) {
        return new ConcordExecutionContext(this, expressionManager, source, processDefinitionId, elementId, protectedVarContext);
    }

    @Override
    public ExecutionContext withOverrides(ExecutionContext delegate, Map<Object, Object> overrides) {
        return new MapBackedExecutionContext(delegate, overrides);
    }

    public static class ConcordExecutionContext extends ExecutionContextImpl implements Context {

        private static final String PROTECTED_VAR_KEY = "__protected_vars";

        private final ProtectedVarContext protectedVarContext;

        public ConcordExecutionContext(ExecutionContextFactory<? extends ExecutionContext> ctxFactory,
                                       ExpressionManager expressionManager, Variables source,
                                       ProtectedVarContext protectedVarContext) {
            this(ctxFactory, expressionManager, source, null, null, protectedVarContext);
        }

        public ConcordExecutionContext(ExecutionContextFactory<? extends ExecutionContext> ctxFactory,
                                       ExpressionManager expressionManager, Variables source,
                                       String processDefinitionId, String elementId,
                                       ProtectedVarContext protectedVarContext) {
            super(ctxFactory, expressionManager, source, processDefinitionId, elementId);
            this.protectedVarContext = protectedVarContext;
        }

        @Override
        public UUID getEventCorrelationId() {
            return (UUID) getVariable(InternalConstants.Context.EVENT_CORRELATION_KEY);
        }

        @Override
        public void setVariable(String key, Object value) {
            if (PROTECTED_VAR_KEY.equals(key)) {
                throw new RuntimeException("Can't set concord internal variables");
            }

            Set<String> protectedVars = getProtectedVariableNames();
            if (protectedVars.contains(key)) {
                throw new RuntimeException("Can't rewrite protected variable with name '" + key + "'");
            }

            super.setVariable(key, value);
        }

        @Override
        public void setProtectedVariable(String key, Object value) {
            assertProtectedVarAccess();

            Set<String> protectedVars = new HashSet<>(getProtectedVariableNames());
            protectedVars.add(key);
            super.setVariable(PROTECTED_VAR_KEY, Collections.unmodifiableSet(protectedVars));
            super.setVariable(key, value);
        }

        @Override
        public Object getProtectedVariable(String key) {
            Set<String> protectedVars = getProtectedVariableNames();
            if (!protectedVars.contains(key)) {
                return null;
            }
            return getVariable(key);
        }

        @Override
        public void removeVariable(String key) {
            if (PROTECTED_VAR_KEY.equals(key)) {
                throw new RuntimeException("Can't remove concord internal variables");
            }

            Set<String> protectedVars = getProtectedVariableNames();
            if (protectedVars.contains(key)) {
                assertProtectedVarAccess();

                Set<String> newVars = new HashSet<>(protectedVars);
                newVars.remove(key);
                super.setVariable(PROTECTED_VAR_KEY, Collections.unmodifiableSet(newVars));
            }

            super.removeVariable(key);
        }

        @SuppressWarnings("unchecked")
        private Set<String> getProtectedVariableNames() {
            Set<String> result = (Set<String>) getVariable(PROTECTED_VAR_KEY);
            if (result == null) {
                return Collections.emptySet();
            }
            return result;
        }

        private void assertProtectedVarAccess() {
            if (!protectedVarContext.hasToken()) {
                throw new RuntimeException("Not allowed to set protected variable");
            }
        }
    }

    public static class MapBackedExecutionContext extends DefaultExecutionContextFactory.MapBackedExecutionContext implements Context {

        public MapBackedExecutionContext(ExecutionContext delegate, Map<Object, Object> overrides) {
            super(delegate, overrides);
        }

        @Override
        public void setProtectedVariable(String key, Object value) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public Object getProtectedVariable(String key) {
            return super.getVariable(key);
        }
    }
}
