package com.walmartlabs.concord.plugins.variables;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.*;

@Named("vars")
public class VariablesTask implements Task {

    public Object get(Context ctx, String key, Object defaultValue) {
        Object v = ctx.getVariable(key);
        return v != null ? v : defaultValue;
    }

    public void set(Context ctx, String targetKey, String sourceKey, String defaultKey) {
        Object v = ctx.getVariable(sourceKey);
        if (v == null) {
            v = ctx.getVariable(defaultKey);
        }
        ctx.setVariable(targetKey, v);
    }

    public void set(Context ctx, Map<String, Object> vars) {
        vars.forEach(ctx::setVariable);
    }

    public Object eval(@InjectVariable("context") Context ctx, Object v) {
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
