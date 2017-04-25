package com.walmartlabs.concord.plugins.nexus.perf2;

import io.takari.bpm.api.ExecutionContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MockExecutionContext implements ExecutionContext {

    private final Map<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public Object getVariable(String key) {
        return values.get(key);
    }

    @Override
    public Map<String, Object> getVariables() {
        return values;
    }

    @Override
    public void setVariable(String key, Object value) {
        values.put(key, value);
    }

    @Override
    public boolean hasVariable(String key) {
        return values.containsKey(key);
    }

    @Override
    public void removeVariable(String key) {
        values.remove(key);
    }

    @Override
    public Set<String> getVariableNames() {
        return values.keySet();
    }

    @Override
    public <T> T eval(String expr, Class<T> type) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<String, Object> toMap() {
        return values;
    }
}
