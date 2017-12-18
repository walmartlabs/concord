package com.walmartlabs.concord.project.yaml;

import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.*;
import io.takari.bpm.context.DefaultExecutionContextFactory;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.ResumeHandler;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.resource.ResourceResolver;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.*;

public class YamlParserTest {

    private final TestWorkflowProvider workflowProvider = new TestWorkflowProvider();
    private TestServiceTaskRegistry taskRegistry;
    private Engine engine;
    private Map<UUID, Form> forms;
    private FormService formService;

    @Before
    public void setUp() throws Exception {
        taskRegistry = new TestServiceTaskRegistry();

        forms = new HashMap<>();
        FormStorage fs = new TestFormStorage(forms);

        ExpressionManager expressionManager = new DefaultExpressionManager(taskRegistry);
        DefaultExecutionContextFactory contextFactory = new DefaultExecutionContextFactory(expressionManager);

        ResumeHandler rs = (form, args) -> getEngine().resume(form.getProcessBusinessKey(), form.getEventName(), args);
        formService = new DefaultFormService(contextFactory, rs, fs);

        ResourceResolver resourceResolver = name -> ClassLoader.getSystemResourceAsStream(name);

        engine = new EngineBuilder()
                .withDefinitionProvider(workflowProvider.processes())
                .withTaskRegistry(taskRegistry)
                .withUserTaskHandler(new FormTaskHandler(contextFactory, workflowProvider.forms(), formService))
                .withResourceResolver(resourceResolver)
                .build();
    }

    private Engine getEngine() {
        return engine;
    }

    private void deploy(String resource) {
        workflowProvider.deploy(resource);
    }

    // PROCESSES (000 - 099)

    @Test(expected = RuntimeException.class)
    public void test000() throws Exception {
        deploy("000.yml");

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);
    }

    @Test
    public void test001() throws Exception {
        deploy("001.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).hello();
    }

    @Test
    public void test002() throws Exception {
        deploy("002.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(2)).hello();
    }

    @Test
    public void test003() throws Exception {
        deploy("003.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq(testValue));
        verify(testBean, times(1)).checkString(eq(testValue));
    }

    @Test
    public void test004() throws Exception {
        deploy("004.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    public void test005() throws Exception {
        deploy("005.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> task -> task -> end
        assertEquals(11, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "main", args);

        // ---

        verify(testTask, times(1)).call(eq("hello"));
        verify(testTask, times(1)).call(eq(testValue));
        verify(testTask, times(1)).call(anyMap());
        verify(testTask, times(1)).call(eq(1), eq(2));
    }

    @Test
    public void test006() throws Exception {
        deploy("006.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        TestTask testTask = spy(new TestTask("bStr", "cStr"));
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "main", args);

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
        verify(testBean, times(1)).toString(eq(testValue));
    }

    @Test
    public void test007() throws Exception {
        deploy("007.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //            /-> task -\   /-> task -\
        // start -> gw -> task -> gw -> task -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("d"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test008() throws Exception {
        deploy("008.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //            /-> task -> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test009() throws Exception {
        deploy("009.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> subprocess -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess: start -> task -> task -> end
        assertEquals(7, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verify(testBean, times(1)).toString(eq("d"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test010() throws Exception {
        deploy("010.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                    /----------------------------->\
        // start -> task -> task + boundary-event -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).throwBpmnError(anyString());
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test011() throws Exception {
        deploy("011.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //            /----------------------------->\
        // start -> task + boundary-event -> task -> task -> end
        assertEquals(11, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        TestErrorTask testErrorTask = spy(new TestErrorTask());
        taskRegistry.register("testErrorTask", testErrorTask);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testErrorTask, times(1)).execute(any(ExecutionContext.class));
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));

        verifyNoMoreInteractions(testBean);
        verifyNoMoreInteractions(testErrorTask);
    }

    @Test
    public void test012() throws Exception {
        deploy("012.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                /------------------------------->\
        // start -> subprocess + boundary-event -> task -> task -> end
        assertEquals(11, pd.getChildren().size());
        // subprocess:
        // start -> task -> task -> end
        assertEquals(7, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).throwBpmnError(anyString());
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test013() throws Exception {
        deploy("013.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> callactiviti -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test014() throws Exception {
        deploy("014.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /------------------->\
        // start -> gw -> callactivity -> end
        assertEquals(8, pd.getChildren().size());

        int loops = 100;

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("cnt", 0);
        args.put("loops", loops);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(loops)).inc(anyInt());
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test015() throws Exception {
        deploy("015.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("interpolation", testValue);
        engine.start(key, "main", args);

        // ---

        String s = "multiline test with\nstring " + testValue;
        verify(testTask, times(1)).call(eq(s));
    }

    @Test
    public void test016() throws Exception {
        deploy("016.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> task -> end
        assertEquals(9, pd.getChildren().size());

        int inputNumber = ThreadLocalRandom.current().nextInt();
        boolean inputBoolean = ThreadLocalRandom.current().nextBoolean();
        String inputString = "test#" + System.currentTimeMillis();

        TestInterface testTask1 = mock(TestInterface.class);
        taskRegistry.register("testTask1", testTask1);

        TestInterface testTask2 = spy(new TestInterface() {
            @Override
            @SuppressWarnings("unchecked")
            public void call(Object arg1) {
                List<Object> l = (List<Object>) arg1;
                assertEquals(inputNumber, l.get(0));
            }
        });
        taskRegistry.register("testTask2", testTask2);

        TestInterface testTask3 = spy(new TestInterface() {
            @Override
            @SuppressWarnings("unchecked")
            public void call(Object arg1) {
                assertNotNull(arg1);

                Map<String, Object> m = (Map<String, Object>) arg1;
                assertEquals(123, m.get("a"));
                assertEquals(inputNumber, m.get("b"));

                List<Object> l = (List<Object>) m.get("c");
                assertEquals(inputBoolean, l.get(0));
                assertEquals(inputString, l.get(1));
            }
        });
        taskRegistry.register("testTask3", testTask3);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("inputNumber", inputNumber);
        args.put("inputBoolean", inputBoolean);
        args.put("inputString", inputString);
        engine.start(key, "main", args);

        // ---

        verify(testTask1, times(1)).call(eq(inputNumber));
        verify(testTask2, times(1)).call(any());
        verify(testTask3, times(1)).call(any());
    }

    @Test
    public void test017() throws Exception {
        deploy("017.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> catchevent -> task -> end
        assertEquals(9, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
        reset(testBean);

        // ---

        engine.resume(key, "ev1", null);

        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test018() throws Exception {
        deploy("018.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("input", 10);
        engine.start(key, "main", args);

        verify(testBean, times(1)).toString(eq(20.0));
    }

    @Test
    public void test019() throws Exception {
        deploy("019.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        verify(testBean, times(1)).toString(eq(12345));
    }

    @Test
    public void test020() throws Exception {
        deploy("020.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        verify(testBean, times(1)).toString(eq(12345));
    }

    @Test
    public void test021() throws Exception {
        deploy("021.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);

        engine.start(key, "main", args);

        // ---
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test021_2() throws Exception {
        deploy("021_2.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        try {
            engine.start(key, "main", args);
            fail("exception expected");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("error-code-2"));
        }

        // ---

        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test022() throws Exception {
        deploy("022.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test022_2() throws Exception {
        deploy("022_2.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        try {
            engine.start(key, "main", args);
            fail("exception expected");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("error-code"));
        }

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test023() throws Exception {
        deploy("023.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test023_2() throws Exception {
        deploy("023.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test024() throws Exception {
        deploy("024.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        // start -> task -> end
        assertEquals(5, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", Collections.emptyMap());

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test025() throws Exception {
        deploy("025.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /----------> end
        // start -> task -> gw -> task -> end
        assertEquals(11, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test025_2() throws Exception {
        deploy("025.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /----------> end
        // start -> task -> gw -> task -> end
        assertEquals(11, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("else"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test026() throws Exception {
        deploy("026.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> task -> end-error
        // start -> task -> gw -> task -> end
        assertEquals(13, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test027() throws Exception {
        deploy("027.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //             /--------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> end
        // start -> task -> gw -> end
        assertEquals(9, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test027_2() throws Exception {
        deploy("027.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //             /--------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> end
        // start -> task -> gw -> end
        assertEquals(9, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028_2() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=1"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028_3() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test029() throws Exception {
        deploy("029.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                         /--> task("success")   ->\
        //                   /--> gw -> task("success=2") ->\
        // start -> task -> gw -> task("success=1")       -> task("success=end") -> end
        assertEquals(19, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=2"));
        verify(testBean, times(1)).toString(eq("success=end"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test030() throws Exception {
        deploy("030.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //           /--> task -> task ---------------->\
        //          /              /--> task -> task -->\
        // start -> gw -> task -> gw -> task ----------> end
        assertEquals(21, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("log", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("name", "foo");
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).info(eq("test"), eq("Hello, foo"));
        verify(testBean, times(1)).info(eq("test -- 3"), eq("Hello, foo"));
        verify(testBean, times(1)).info(eq("test -- 4"), eq("Hello, foo"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test031() throws Exception {
        deploy("031.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        //                          /--> task -->\
        // start -> subprocess + boundary-event -> end
        assertEquals(9, pd.getChildren().size());

        // subprocess
        // start -> callactiviti -> end
        assertEquals(5, findSubprocess(pd).getChildren().size());

        pd = workflowProvider.processes().getById("myOtherFlow");

        // callactiviti
        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("in-call-activiti"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test032() throws Exception {
        deploy("032.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        taskRegistry.register("vars", testBean);
        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("1234"));
        verify(testBean, times(1)).toString(eq("12341"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test033() throws Exception {
        String txId = UUID.randomUUID().toString();

        // ---

        deploy("033.yml");

        DockerTask task = spy(new DockerTask());
        taskRegistry.register("docker", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("workDir", "/tmp");
        args.put("txId", txId);
        engine.start(key, "main", args);

        // ---
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(task, times(1)).call(eq("test-image"), anyBoolean(), anyBoolean(), eq("test-cmd"), c.capture(), eq("/tmp"));

        Map<String, Object> m = c.getValue();
        assertNotNull(m);
        assertEquals(2, m.size());
        assertEquals(123, m.get("x"));
        assertEquals(txId, m.get("y"));
    }

    @Test
    public void test034() throws Exception {
        deploy("034.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", new HashMap<>());
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(isNull());
    }

    @Test
    public void test035() throws Exception {
        deploy("035.yml");

        JavaDelegate task = spy(new JavaDelegate() {
            @Override
            public void execute(ExecutionContext ctx) throws Exception {
                Object o = ctx.getVariable("aList");
                assertTrue(o instanceof List);

                List l = (List) o;
                assertEquals(3, l.size());
                assertEquals(132, l.get(2));
            }
        });
        taskRegistry.register("testTask", task);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(task, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    public void test036() throws Exception {
        deploy("036.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", 1L);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq(101L));
        verify(testBean, times(1)).toString(eq(102L));
        verify(testBean, times(1)).toString(eq(103L));
        verify(testBean, times(1)).toString(eq(104L));
        verify(testBean, times(1)).toString(eq("handled!"));
    }

    @Test
    public void test037() throws Exception {
        deploy("037.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("1"));
        verify(testBean, times(1)).toString(eq("3"));
    }

    @Test
    public void test040() throws Exception {
        deploy("040.yml");

        ProcessDefinition pd = workflowProvider.processes().getById("main");
        //                   /--> taskA1 -> taskA2 -\
        // start -> task -> gw -> taskB ------------> end
        //                   \--> taskD ------------/
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "a");
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do a"));
        verify(testBean, times(1)).toString(eq("do a2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test040_2() throws Exception {
        deploy("040.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "b");
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test040_3() throws Exception {
        deploy("040.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "123");
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do default"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test041() throws Exception {
        deploy("041.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 1);
        engine.start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do 1"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test042() throws Exception {
        deploy("042.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 42);
        engine.start(key, "main", args);

        verify(testBean, times(1)).toString(eq("after switch/case"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test043() throws Exception {
        deploy("043.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 2);
        args.put("caseValue1", 10);
        args.put("caseValue2", 2);
        engine.start(key, "main", args);

        verify(testBean, times(1)).toString(eq("do 2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test044() throws Exception {
        deploy("044.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "default");
        engine.start(key, "main", args);

        verify(testBean, times(1)).toString(eq("do default as string"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test045() throws Exception {
        deploy("045.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("err"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test046() throws Exception {
        deploy("046.yml");

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);
    }

    // FORMS (100 - 199)

    @Test
    public void test100() throws Exception {
        deploy("100.yml");

        String formValue = "test#" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        verify(testBean, times(1)).toString(eq("aaa"));

        // ---

        UUID formId = getFirstFormId();
        Map<String, Object> data = Collections.singletonMap("name", formValue);
        formService.submit(formId, data);

        verify(testBean, times(1)).toString(eq(formValue));
    }

    @Test
    public void test101() throws Exception {
        deploy("101.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = formService.submit(formId, Collections.singletonMap("age", 256));
        assertFalse(result.isValid());

        Map<String, Object> data = new HashMap<>();
        data.put("age", 64);
        data.put("percent", -1.0);

        result = formService.submit(formId, data);
        assertFalse(result.isValid());

        result = formService.submit(formId, Collections.singletonMap("age", 64));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq(64));
    }

    @Test
    public void test102() throws Exception {
        deploy("102.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        int[] numbers = new int[]{-1, 5, 98};

        FormSubmitResult result = formService.submit(formId, Collections.singletonMap("favouriteNumbers", numbers));
        assertFalse(result.isValid());

        numbers = new int[]{0, 5, 98};

        result = formService.submit(formId, Collections.singletonMap("favouriteNumbers", numbers));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq(numbers));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test103() throws Exception {
        deploy("103.yml");

        String inputValue = "test#" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", Collections.singletonMap("inputValue", inputValue));

        // ---

        UUID formId = getFirstFormId();
        Form f = formService.get(formId);

        Map<String, Object> data = (Map<String, Object>) f.getEnv().get("myForm");
        FormSubmitResult result = formService.submit(formId, data);
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("Hello, " + inputValue));
    }

    @Test
    public void test104() throws Exception {
        deploy("104.yml");

        String valueA = "a_" + System.currentTimeMillis();
        String valueB = "b_" + System.currentTimeMillis();
        String valueC = "c_" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean(new String[]{valueA, valueB, valueC}));
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        Map<String, Object> data = Collections.singletonMap("testValue", valueB);
        FormSubmitResult result = formService.submit(formId, data);
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq(valueB));
    }

    @Test
    public void test105() throws Exception {
        deploy("105.yml");

        String valueA = "a_" + System.currentTimeMillis();
        String valueB = "b_" + System.currentTimeMillis();
        String valueC = "c_" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean(new String[]{valueA, valueB, valueC}));
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = formService.submit(formId, Collections.emptyMap());
        assertFalse(result.isValid());

        verify(testBean, never()).toString(any());
    }

    @Test
    public void test106() throws Exception {
        deploy("106.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = formService.submit(formId, Collections.singletonMap("testValue", "d"));
        assertFalse(result.isValid());

        result = formService.submit(formId, Collections.singletonMap("testValue", "a"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("a"));
    }

    @Test
    public void test107() throws Exception {
        deploy("107.yml");

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = formService.submit(formId, Collections.singletonMap("testValue", "else"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("else"));
    }

    @Test
    public void testJunk() throws Exception {
        deploy("junk.yml");
    }

    // MISC

    @Test(expected = RuntimeException.class)
    public void testOld() throws Exception {
        deploy("old.yml");
    }

    private ProcessDefinition findSubprocess(ProcessDefinition pd) {
        return pd.getChildren().stream()
                .filter(e -> e instanceof ProcessDefinition)
                .map(e -> (ProcessDefinition) e)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("subprocess not found"));
    }

    private UUID getFirstFormId() {
        if (forms == null || forms.isEmpty()) {
            return null;
        }
        return forms.keySet().iterator().next();
    }

    private static class TestBean {

        private final String[] items;

        public TestBean() {
            this(null);
        }

        public TestBean(String[] items) {
            this.items = items;
        }

        public void hello() {
        }

        public String toString(Object s) {
            if (s == null) {
                return null;
            }
            return s.toString();
        }

        public void checkString(String s) {
        }

        public void throwBpmnError(String errorRef) {
            throw new BpmnError(errorRef);
        }

        public int inc(int i) {
            return i + 1;
        }

        public String[] getItems() {
            return items;
        }

        public void info(String a, String b) {
            // do nothing
        }

        public void set(ExecutionContext executionContext, Map<String, Object> vars) {
            vars.forEach(executionContext::setVariable);
        }
    }

    private static class TestTask implements JavaDelegate {

        private final String srcVar;
        private final String dstVar;

        public TestTask() {
            this(null, null);
        }

        private TestTask(String srcVar, String dstVar) {
            this.srcVar = srcVar;
            this.dstVar = dstVar;
        }

        @Override
        public void execute(ExecutionContext ctx) throws Exception {
            if (srcVar != null && dstVar != null) {
                ctx.setVariable(dstVar, ctx.getVariable(srcVar));
            }
        }

        public void call(Object arg1) {
        }

        public void call(Object arg1, Object arg2) {
        }
    }

    private static class DockerTask implements Task {

        public void call(String dockerImage, boolean forcePull, boolean debug, String cmd, Map<String, Object> env, String payloadPath) throws Exception {
        }
    }

    private interface TestInterface {

        void call(Object arg1);
    }

    private static class TestErrorTask implements JavaDelegate {

        @Override
        public void execute(ExecutionContext ctx) throws Exception {
            throw new BpmnError("boom!");
        }
    }

    private static class TestWorkflowProvider {

        private final ProjectLoader projectLoader = new ProjectLoader();
        private final Map<String, ProcessDefinition> processes = new HashMap<>();
        private final Map<String, FormDefinition> forms = new HashMap<>();

        public void deploy(String resource) {
            ProjectDefinition wd = loadWorkflow(resource);
            processes.putAll(wd.getFlows());
            forms.putAll(wd.getForms());
        }

        public ProcessDefinitionProvider processes() {
            return processes::get;
        }

        public FormDefinitionProvider forms() {
            return forms::get;
        }

        private ProjectDefinition loadWorkflow(String resource) {
            try (InputStream in = ClassLoader.getSystemResourceAsStream(resource)) {
                return projectLoader.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Error while loading a definition", e);
            }
        }
    }

    private static class TestServiceTaskRegistry implements ServiceTaskRegistry {

        private final Map<String, Object> entries = new HashMap<>();

        public void register(String k, Object v) {
            entries.put(k, v);
        }

        @Override
        public Object getByKey(String k) {
            return entries.get(k);
        }
    }

    private static class TestFormStorage implements FormStorage {

        private final Map<UUID, Form> forms;

        private TestFormStorage(Map<UUID, Form> forms) {
            this.forms = forms;
        }

        @Override
        public void save(Form form) {
            forms.put(form.getFormInstanceId(), form);
        }

        @Override
        public void complete(UUID formInstanceId) throws ExecutionException {
            forms.remove(formInstanceId);
        }

        @Override
        public Form get(UUID formId) {
            return forms.get(formId);
        }
    }
}
