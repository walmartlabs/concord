package com.walmartlabs.concord.sdk;

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
    public String getProcessDefinitionId() {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public String getElementId() {
        throw new IllegalStateException("Not supported");
    }
}
