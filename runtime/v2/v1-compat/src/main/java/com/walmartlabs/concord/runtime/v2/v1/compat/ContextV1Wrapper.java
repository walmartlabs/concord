package com.walmartlabs.concord.runtime.v2.v1.compat;

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

import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;

import java.io.Serializable;
import java.util.*;

public class ContextV1Wrapper implements com.walmartlabs.concord.sdk.Context {

    private final TaskContext ctx;
    private final Map<String, Object> allVariables = new HashMap<>();
    private final Map<String, Serializable> result;

    public ContextV1Wrapper(TaskContext ctx, Map<String, Serializable> result) {
        this.ctx = ctx;
        this.result = result;

        this.allVariables.putAll(collectAllVariables(ctx));
    }

    @Override
    public Object getVariable(String key) {
        return allVariables.get(key);
    }

    @Override
    public void setVariable(String key, Object value) {
        if (value instanceof Serializable) {
            result.put(key, (Serializable) value);
        }

        allVariables.put(key, value);
    }

    @Override
    public void removeVariable(String key) {
        result.remove(key);
        allVariables.remove(key);
    }

    @Override
    public void setProtectedVariable(String key, Object value) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Object getProtectedVariable(String key) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Set<String> getVariableNames() {
        return allVariables.keySet();
    }

    @Override
    public UUID getEventCorrelationId() {
        return null;
    }

    @Override
    public <T> T eval(String expr, Class<T> type) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Object interpolate(Object v) {
        return ctx.eval(v, Object.class);
    }

    @Override
    public Object interpolate(Object v, Map<String, Object> variables) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(allVariables);
    }

    @Override
    public void suspend(String eventName) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void suspend(String eventName, Object payload, boolean resumeFromSameStep) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void form(String formName, Map<String, Object> formOptions) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getProcessDefinitionId() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getElementId() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String getCurrentFlowName() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Map<String, Object> getAllVariables() {
        return new HashMap<>(allVariables);
    }

    private static Map<String, Object> collectAllVariables(TaskContext ctx) {
        return new HashMap<>(ctx.input());
    }
}
