package com.walmartlabs.concord.project.yaml;

import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.*;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anyString;
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
        ResumeHandler rs = (form, args) -> getEngine().resume(form.getProcessBusinessKey(), form.getEventName(), args);
        formService = new DefaultFormService(rs, fs, expressionManager);

        ResourceResolver resourceResolver = name -> ClassLoader.getSystemResourceAsStream(name);

        engine = new EngineBuilder()
                .withDefinitionProvider(workflowProvider.processes())
                .withTaskRegistry(taskRegistry)
                .withUserTaskHandler(new FormTaskHandler(workflowProvider.forms(), formService))
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
        verify(testTask2, times(1)).call(anyObject());
        verify(testTask3, times(1)).call(anyObject());
    }

    @Test
    public void test017() throws Exception {
        deploy("017.yml");

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

        verify(testBean, never()).toString(anyObject());
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
