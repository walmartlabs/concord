package com.walmartlabs.concord.plugins.yaml2;

import com.walmartlabs.concord.common.format.ParserException;
import io.takari.bpm.EngineBuilder;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.*;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.task.ServiceTaskRegistry;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class YamlParserTest {

    private TestServiceTaskRegistry taskRegistry;
    private Engine engine;

    @Before
    public void setUp() throws Exception {
        taskRegistry = new TestServiceTaskRegistry();
        engine = new EngineBuilder()
                .withDefinitionProvider(new TestDefinitionProvider())
                .withTaskRegistry(taskRegistry)
                .build();
    }

    @Test(expected = RuntimeException.class)
    public void test000() throws Exception {
        String key = UUID.randomUUID().toString();
        engine.start(key, "000/main", null);
    }

    @Test
    public void test001() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "001/main", null);

        // ---

        verify(testBean, times(1)).hello();
    }

    @Test
    public void test002() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "002/main", null);

        // ---

        verify(testBean, times(2)).hello();
    }

    @Test
    public void test003() throws Exception {
        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "003/main", args);

        // ---

        verify(testBean, times(1)).toString(eq(testValue));
        verify(testBean, times(1)).checkString(eq(testValue));
    }

    @Test
    public void test004() throws Exception {
        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "004/main", null);

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    public void test005() throws Exception {
        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "005/main", args);

        // ---

        verify(testTask, times(1)).call(eq("hello"));
        verify(testTask, times(1)).call(eq(testValue));
        verify(testTask, times(1)).call(anyMap());
        verify(testTask, times(1)).call(eq(1), eq(2));
    }

    @Test
    public void test006() throws Exception {
        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        TestTask testTask = spy(new TestTask("bStr", "cStr"));
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        engine.start(key, "006/main", args);

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
        verify(testBean, times(1)).toString(eq(testValue));
    }

    @Test
    public void test007() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        engine.start(key, "007/main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("d"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test008() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        engine.start(key, "008/main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test009() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "009/main", null);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verify(testBean, times(1)).toString(eq("d"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test010() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "010/main", null);

        // ---

        verify(testBean, times(1)).throwBpmnError(anyString());
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test011() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        TestErrorTask testErrorTask = spy(new TestErrorTask());
        taskRegistry.register("testErrorTask", testErrorTask);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "011/main", null);

        // ---

        verify(testErrorTask, times(1)).execute(any(ExecutionContext.class));
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));

        verifyNoMoreInteractions(testBean);
        verifyNoMoreInteractions(testErrorTask);
    }

    @Test
    public void test012() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "012/main", null);

        // ---

        verify(testBean, times(1)).throwBpmnError(anyString());
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test013() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "013/main", null);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test014() throws Exception {
        int loops = 100;

        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("cnt", 0);
        args.put("loops", loops);
        engine.start(key, "014/main", args);

        // ---

        verify(testBean, times(loops)).inc(anyInt());
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test015() throws Exception {
        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        taskRegistry.register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("interpolation", testValue);
        engine.start(key, "015/main", args);

        // ---

        String s = "multiline test with\nstring " + testValue;
        verify(testTask, times(1)).call(eq(s));
    }

    @Test
    public void test016() throws Exception {
        int inputNumber = ThreadLocalRandom.current().nextInt();
        boolean inputBoolean = ThreadLocalRandom.current().nextBoolean();
        String inputString = "test#" + System.currentTimeMillis();

        TestInterface testTask1 = mock(TestInterface.class);
        taskRegistry.register("testTask1", testTask1);

        TestInterface testTask2 = spy(new TestInterface() {
            @Override
            public void call(Object arg1) {
                List<Object> l = (List<Object>) arg1;
                assertEquals(inputNumber, l.get(0));
            }
        });
        taskRegistry.register("testTask2", testTask2);

        TestInterface testTask3 = spy(new TestInterface() {
            @Override
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
        engine.start(key, "016/main", args);

        // ---

        verify(testTask1, times(1)).call(eq(inputNumber));
        verify(testTask2, times(1)).call(anyObject());
        verify(testTask3, times(1)).call(anyObject());
    }

    @Test
    public void test017() throws Exception {
        TestBean testBean = spy(new TestBean());
        taskRegistry.register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        engine.start(key, "017/main", null);

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
        reset(testBean);

        // ---

        engine.resume(key, "ev1", null);

        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test(expected = RuntimeException.class)
    public void testOld() throws Exception {
        String key = UUID.randomUUID().toString();
        engine.start(key, "old/simple1", null);
    }

    private static class TestBean {

        public void hello() {
        }

        public String toString(Object s) {
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

    private static class TestDefinitionProvider implements ProcessDefinitionProvider {

        private final Map<String, ProcessDefinition> cache = new HashMap<>();

        @Override
        public ProcessDefinition getById(String id) throws ExecutionException {
            return cache.computeIfAbsent(id, k -> {

                String[] as = k.split("/");

                String resource = as[0] + ".yml";
                String entryPoint = as[1];

                try (InputStream in = ClassLoader.getSystemResourceAsStream(resource)) {
                    YamlParser p = new YamlParser();
                    for (ProcessDefinition pd : p.parse(in)) {
                        if (entryPoint.equals(pd.getId())) {
                            return pd;
                        }
                    }
                } catch (ParserException | IOException e) {
                    throw new RuntimeException("Error while loading a definition", e);
                }

                throw new RuntimeException("Definition not found: " + k);
            });
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
}
