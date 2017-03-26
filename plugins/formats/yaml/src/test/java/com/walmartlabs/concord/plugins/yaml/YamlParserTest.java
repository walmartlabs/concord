package com.walmartlabs.concord.plugins.yaml;

import com.walmartlabs.concord.common.format.WorkflowDefinition;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.interceptors.ExecutionInterceptor;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.testkit.*;
import io.takari.bpm.xml.ParserException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class YamlParserTest {

    @Rule
    public EngineRule engineRule = new EngineRule(new DeploymentProcessor() {
        @Override
        public void handle(InputStream in, TestProcessDefinitionProvider provider) throws ParserException {
            YamlParser p = new YamlParser();
            try {
                WorkflowDefinition wd = p.parse("n/a", in);
                for (ProcessDefinition pd : wd.getProcesses().values()) {
                    provider.add(pd);
                }
            } catch (com.walmartlabs.concord.common.format.ParserException e) {
                throw new ParserException("Resource parsing error", e);
            }
        }
    });

    private ExecutionInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = mock(ExecutionInterceptor.class);
        engineRule.getEngine().addInterceptor(interceptor);
    }

    @Test
    @Deployment(resources = {"simple.yml"})
    public void testSimple1() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple1", null);

        // ---

        verify(b, times(1)).sayHello();
    }

    @Test
    @Deployment(resources = {"simple.yml"})
    public void testSimple2() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple2", null);

        // ---

        verify(b, times(1)).sayHello();
        verify(b, times(1)).sayBye();
    }

    @Test
    @Deployment(resources = {"simple.yml"})
    public void testSimple3() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple3", null);

        // ---

        verify(b, times(2)).sayHello();
        verify(b, times(1)).sayBye();
    }

    @Test
    @Deployment(resources = {"simple.yml"})
    public void testSimple4() throws Exception {
        try {
            engineRule.startProcessInstanceByKey("simple4", null);
            fail("should fail");
        } catch (ExecutionException e) {
        }

        // ---

        verify(interceptor).onFailure(anyString(), eq("kaboom"));
    }

    @Test
    @Deployment(resources = {"simple.yml"})
    public void testSimple5() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---
        try {
            engineRule.startProcessInstanceByKey("simple5", null);
            fail("should fail");
        } catch (ExecutionException e) {
        }

        // ---

        verify(b, times(1)).sayHello();
        verify(interceptor, times(1)).onFailure(anyString(), eq("myError"));
    }

    @Test
    @Deployment(resources = {"simple6.yml"})
    public void testSimple6() throws Exception {
        String name = "test#" + System.currentTimeMillis();

        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        Map<String, Object> args = new HashMap<>();
        args.put("myName", name);

        engineRule.startProcessInstanceByKey("simple6", args);

        // ---

        verify(b, times(1)).sayHello(eq(name));
        verify(b, times(1)).sayHello(eq("A literal"));
    }

    @Test
    @Deployment(resources = {"switches.yml"})
    public void testSimple7() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        try {
            engineRule.startProcessInstanceByKey("simple7", null);
            fail("should fail");
        } catch (ExecutionException e) {
        }

        // ---

        verify(b, times(1)).sayHello();
        verify(interceptor, times(1)).onFailure(anyString(), eq("err2"));
    }

    @Test
    @Deployment(resources = {"switches.yml"})
    public void testSimple8() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple8", null);

        // ---

        verify(b, times(1)).sayHello();
    }

    @Test
    @Deployment(resources = {"switches.yml"})
    public void testSimple9() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple9", null);

        // ---

        verify(b, times(1)).sayHello();
    }

    @Test
    @Deployment(resources = {"switches.yml"})
    public void testSimple10() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple10", null);

        // ---

        verify(b, times(1)).sayHello();
    }

    @Test
    @Deployment(resources = {"switches.yml"})
    public void testSimple11() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("simple11", null);

        // ---

        verifyZeroInteractions(b);
    }

    @Test
    @Deployment(resources = {"errorsWithParams.yml"})
    public void testErrorWithParams() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Fake");
        try {
            engineRule.startProcessInstanceByKey("errorsWithParams", args);
            fail("should fail");
        } catch (ExecutionException e) {
        }

        // ---

        verify(b, times(1)).doThrow(anyString());
        verify(b, times(1)).sayHello(eq("A literal"));
    }

    @Test
    @Deployment(resources = {"inParams.yml"})
    public void testArrays() throws Exception {
        TestBean b = spy(new TestBean());
        Mocks.register("test", b);

        // ---

        engineRule.startProcessInstanceByKey("main", null);

        // ---

        verify(b, times(1)).checkArray(anyObject(), anyInt());
        verify(b, times(1)).checkString(anyObject());
    }

    public static class TestBean {

        public void sayHello() {
            System.out.println("Hello!");
        }

        public void sayHello(String name) {
            System.out.println("Hello, " + name);
        }

        public void sayBye() {
            System.out.println("Bye!");
        }

        public void doThrow(String errorRef) {
            throw new BpmnError(errorRef);
        }

        public void checkArray(Object arr, int len) {
            assertTrue(arr instanceof Collection);
            assertEquals(len, ((Collection) arr).size());
        }

        public void checkString(Object o) {
            assertTrue(o instanceof String);
        }
    }
}
