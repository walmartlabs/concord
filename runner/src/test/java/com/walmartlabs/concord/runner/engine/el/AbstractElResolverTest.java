package com.walmartlabs.concord.runner.engine.el;

import com.sun.el.lang.EvaluationContext;
import io.takari.bpm.api.ExecutionContext;

import javax.el.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractElResolverTest {

    private ExecutionContext execution = mock(ExecutionContext.class);

    protected void mockVariables(String varName, Object varValue) {
        when(execution.getVariable(eq(varName))).thenReturn(varValue);
    }

    protected ELContext createContext() {
        ValueExpression ve = mock(ValueExpression.class);
        when(ve.getValue(any())).thenReturn(execution);

        VariableMapper varMapper = mock(VariableMapper.class);
        when(varMapper.resolveVariable("execution")).thenReturn(ve);

        ELContext ctx = new ELContext() {
            @Override
            public ELResolver getELResolver() {
                return null;
            }

            @Override
            public FunctionMapper getFunctionMapper() {
                return null;
            }

            @Override
            public VariableMapper getVariableMapper() {
                return varMapper;
            }
        };
        return new EvaluationContext(ctx, null, null);
    }
}
