package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.ExecutionContext;

import javax.inject.Named;

@Named("vars")
public class VariablesTask implements Task {

    public Object get(ExecutionContext ctx, String key, Object defaultValue) {
        Object v = ctx.getVariable(key);
        return v != null ? v : defaultValue;
    }

    public void set(ExecutionContext ctx, String targetKey, String sourceKey, String defaultKey) {
        Object v = ctx.getVariable(sourceKey);
        if (v == null) {
            v = ctx.getVariable(defaultKey);
        }
        ctx.setVariable(targetKey, v);
    }
}
