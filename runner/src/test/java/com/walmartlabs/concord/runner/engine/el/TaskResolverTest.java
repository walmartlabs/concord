package com.walmartlabs.concord.runner.engine.el;

import com.walmartlabs.concord.sdk.InjectVariable;
import io.takari.bpm.task.ServiceTaskRegistryImpl;
import org.junit.Before;
import org.junit.Test;

import javax.el.ELContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;

public class TaskResolverTest extends AbstractElResolverTest {

    private ServiceTaskRegistryImpl registry;
    private ELContext elContext;
    private TaskResolver resolver;

    @Before
    public void setUp() throws Exception {
        registry = new ServiceTaskRegistryImpl();
        resolver = new TaskResolver(registry);
        elContext = createContext();
    }

    @Test
    public void test() throws Exception {
        TestTask task = spy(new TestTask());
        registry.register("test", task);

        mockVariables("var1", "var1-value");

        resolver.getValue(elContext, null, "test");

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
