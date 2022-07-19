package com.walmartlabs.concord.runner.engine.el;

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

import com.sun.el.lang.EvaluationContext;
import io.takari.bpm.api.ExecutionContext;

import javax.el.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractElResolverTest {

    private final ExecutionContext execution = mock(ExecutionContext.class);

    protected void mockVariables(String varName, Object varValue) {
        when(execution.getVariable(eq(varName))).thenReturn(varValue);
    }

    protected ELContext createContext() {
        ValueExpression ve = mock(ValueExpression.class);
        when(ve.getValue(any())).thenReturn(execution);

        VariableMapper varMapper = mock(VariableMapper.class);
        when(varMapper.resolveVariable("execution")).thenReturn(ve);
        when(varMapper.resolveVariable("context")).thenReturn(ve);

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
