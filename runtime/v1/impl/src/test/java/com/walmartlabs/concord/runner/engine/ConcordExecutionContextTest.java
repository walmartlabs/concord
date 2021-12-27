package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.ProtectedTasksPolicy;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionContextFactory;
import io.takari.bpm.api.Variables;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.form.FormService;
import org.junit.jupiter.api.Test;

import static com.walmartlabs.concord.runner.engine.ConcordExecutionContextFactory.ConcordExecutionContext;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

public class ConcordExecutionContextTest {

    private final ProtectedVarContext varContext = new ProtectedVarContext(policyEngine());

    @Test
    public void testProtectedVariableWithoutContext() {
        assertThrows(RuntimeException.class, () -> {
            ConcordExecutionContext ctx = ctx();
            ctx.setProtectedVariable("oops", 1);
        });
    }

    @Test
    public void testProtectedVariable() {
        ConcordExecutionContext ctx = ctx();

        varContext.preTask("task", null, null);
        ctx.setProtectedVariable("s", "s-var");
        varContext.postTask("task", null,null);

        assertEquals("s-var", ctx.getVariable("s"));
        assertEquals("s-var", ctx.getProtectedVariable("s"));

        assertNull(ctx.getVariable("not-found"));
        assertNull(ctx.getProtectedVariable("not-found"));

        try {
            ctx.removeVariable("s");
            fail("exception expected");
        } catch (RuntimeException e) {
            // ignore
        }
    }

    private ConcordExecutionContext ctx() {
        ExecutionContextFactory<? extends ExecutionContext> ctxFactory = null;
        ExpressionManager expressionManager = null;
        FormService formService = null;
        Variables source = new Variables();
        return new ConcordExecutionContext(ctxFactory, expressionManager, source, varContext, formService);
    }

    private static PolicyEngine policyEngine() {
        ProtectedTasksPolicy protectedTasksPolicy = mock(ProtectedTasksPolicy.class);
        when(protectedTasksPolicy.isProtected(eq("task"))).thenReturn(true);

        PolicyEngine policyEngine = mock(PolicyEngine.class);
        when(policyEngine.getProtectedTasksPolicy()).thenReturn(protectedTasksPolicy);
        return policyEngine;
    }
}
