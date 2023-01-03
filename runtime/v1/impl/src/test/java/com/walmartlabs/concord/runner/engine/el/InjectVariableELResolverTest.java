package com.walmartlabs.concord.runner.engine.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.InjectVariable;
import io.takari.bpm.api.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.el.ELContext;

import static org.mockito.Mockito.*;

public class InjectVariableELResolverTest extends AbstractElResolverTest {

    private ELContext elContext;
    private InjectVariableELResolver resolver;

    @BeforeEach
    public void setUp() {
        resolver = new InjectVariableELResolver();
        elContext = createContext();
    }

    @Test
    public void callWithoutInject() {
        TestTask task = spy(new TestTask());
        Object method = "callWithInject";
        Class<?>[] paramTypes = null;
        Object[] paramValues = null;

        resolver.invoke(elContext, task, method, paramTypes, paramValues);

        verify(task, times(0)).callWithInject();
    }

    @Test
    public void callWithInjectOnlyInjectedVars() {
        TestTask task = spy(new TestTask());
        Object method = "callWithInject1";
        Class<?>[] paramTypes = null;
        Object[] paramValues = null;

        resolver.invoke(elContext, task, method, paramTypes, paramValues);

        verify(task, times(1)).callWithInject1(any(ExecutionContext.class));
    }

    @Test
    public void callWithInject() {
        TestTask task = spy(new TestTask());
        Object method = "callWithInject1";
        Class<?>[] paramTypes = null;
        Object[] paramValues = {"xxxx"};

        resolver.invoke(elContext, task, method, paramTypes, paramValues);

        verify(task, times(1)).callWithInject1(any(ExecutionContext.class), eq("xxxx"));
    }

    @Test
    public void callWithInject2() {
        TestTask task = spy(new TestTask());
        Object method = "callWithInject2";
        Class<?>[] paramTypes = null;
        Object[] paramValues = {"xxxx"};

        mockVariables("injectedVar", "another-var");

        resolver.invoke(elContext, task, method, paramTypes, paramValues);

        verify(task, times(1)).callWithInject2(any(ExecutionContext.class), eq("another-var"), eq("xxxx"));
    }

    private static class TestTask {

        public void callWithInject() {
            System.out.println("callWithInject");
        }

        public void callWithInject1(
                @InjectVariable("execution") ExecutionContext executionContext) {
            System.out.println("callWithInject ['" + executionContext + "']");
        }

        public void callWithInject1(
                @InjectVariable("execution") ExecutionContext executionContext,
                String var1) {
            System.out.println("callWithInject ['" + executionContext + "', '" + var1 + "]");
        }

        public void callWithInject2(
                @InjectVariable("execution") ExecutionContext injectedVar1,
                @InjectVariable("injectedVar") String injectedVar2,
                String var1) {
            System.out.println("callWithInject ['" + injectedVar1 + "', '" + injectedVar2 + "', '" + injectedVar2 + "]");
        }
    }
}
