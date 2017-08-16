package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.ExecutionContext;

import javax.inject.Named;
import java.util.*;

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

    public void set(ExecutionContext ctx, Map<String, Object> vars) {
        vars.forEach(ctx::setVariable);
    }

    public Object eval(@InjectVariable("execution") ExecutionContext ctx, Object v) {
        return ctx.interpolate(v);
    }

    public List<Object> concat(Collection<Object> a, Collection<Object> b) {
        if (a == null) {
            a = Collections.emptyList();
        }

        if (b == null) {
            b = Collections.emptyList();
        }

        List<Object> l = new ArrayList<>(a.size() + b.size());
        l.addAll(a);
        l.addAll(b);
        return l;
    }
}
