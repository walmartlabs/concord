package com.walmartlabs.concord.runner.engine.el;

import com.sun.el.lang.EvaluationContext;
import com.walmartlabs.concord.project.InternalConstants;
import io.takari.bpm.api.ExecutionContext;

import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

public final class ResolverUtils {

    public static Object getVariable(ELContext context, String name) {
        VariableMapper varMapper = ((EvaluationContext) context).getELContext().getVariableMapper();
        ValueExpression v = varMapper.resolveVariable(name);
        if(v != null) {
            return v.getValue(context);
        }
        v = varMapper.resolveVariable(InternalConstants.Context.CONTEXT_KEY);
        if(v != null) {
            ExecutionContext ctx = (ExecutionContext) v.getValue(context);
            return ctx.getVariable(name);
        }
        return null;
    }

    private ResolverUtils() {
    }
}
