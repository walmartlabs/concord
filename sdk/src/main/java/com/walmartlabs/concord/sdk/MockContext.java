package com.walmartlabs.concord.sdk;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mock implementation of {@link Context}.
 * Useful for unit testing of plugins.
 */
public class MockContext implements Context {

    private final Map<String, Object> delegate;

    public MockContext(Map<String, Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object getVariable(String key) {
        return delegate.get(key);
    }

    @Override
    public void setVariable(String key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void removeVariable(String key) {
        delegate.remove(key);
    }

    @Override
    public Set<String> getVariableNames() {
        return delegate.keySet();
    }

    @Override
    public void setProtectedVariable(String key, Object value) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Object getProtectedVariable(String key) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public <T> T eval(String expr, Class<T> type) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Object interpolate(Object v) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Map<String, Object> toMap() {
        return new HashMap<>(delegate);
    }

    @Override
    public void suspend(String eventName) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public void suspend(String eventName, Object payload) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public String getProcessDefinitionId() {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public String getElementId() {
        throw new IllegalStateException("Not supported");
    }
}
