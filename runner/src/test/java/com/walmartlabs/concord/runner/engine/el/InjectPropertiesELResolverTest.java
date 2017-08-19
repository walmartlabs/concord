package com.walmartlabs.concord.runner.engine.el;

import com.walmartlabs.concord.sdk.InjectVariable;
import org.junit.Before;
import org.junit.Test;

import javax.el.ELContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;

public class InjectPropertiesELResolverTest extends AbstractElResolverTest {

    private ELContext elContext;
    private InjectPropertiesELResolver resolver;

    @Before
    public void setUp() throws Exception {
        resolver = new InjectPropertiesELResolver();
        elContext = createContext();
    }

    @Test
    public void test() throws Exception {
        TestTask task = spy(new TestTask());
        Object method = "whatever";
        Class<?>[] paramTypes = null;
        Object[] paramValues = null;

        mockVariables("var1", "var1-value");

        resolver.invoke(elContext, task, method, paramTypes, paramValues);

        assertEquals("var1-value", task.value);
    }

    private static class TestTask {

        @InjectVariable("var1")
        private String value;

        public void setValue(String value) {
            this.value = value;
        }
    }
}
