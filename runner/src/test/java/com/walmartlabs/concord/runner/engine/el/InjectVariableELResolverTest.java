package com.walmartlabs.concord.runner.engine.el;

import com.sun.el.lang.EvaluationContext;
import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.runner.engine.el.InjectVariableELResolver;
import io.takari.bpm.api.ExecutionContext;
import org.junit.Before;
import org.junit.Test;

import javax.el.*;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class InjectVariableELResolverTest extends AbstractElResolverTest {

    private ELContext elContext;
    private InjectVariableELResolver resolver;

    @Before
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
