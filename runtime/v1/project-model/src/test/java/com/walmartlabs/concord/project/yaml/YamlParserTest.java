package com.walmartlabs.concord.project.yaml;

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

import com.walmartlabs.concord.project.yaml.converter.StepConverter;
import com.walmartlabs.concord.project.yaml.converter.YamlTaskStepConverter;
import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.JavaDelegate;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult;
import io.takari.bpm.model.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.*;

public class YamlParserTest extends AbstractYamlParserTest {

    // PROCESSES (000 - 099)

    @Test
    public void test000() {
        assertThrows(RuntimeException.class, () -> {
            deploy("000.yml");

            String key = UUID.randomUUID().toString();

            start(key, "main", null);
        });
    }

    @Test
    public void test001() throws Exception {
        deploy("001.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main");

        // ---

        verify(testBean, times(1)).hello();
    }

    @Test
    public void test002() throws Exception {
        deploy("002.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main");

        // ---

        verify(testBean, times(2)).hello();
    }

    @Test
    public void test003() throws Exception {
        deploy("003.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq(testValue));
        verify(testBean, times(1)).checkString(eq(testValue));
    }

    @Test
    public void test004() throws Exception {
        deploy("004.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestTask testTask = spy(new TestTask());
        register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main");

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    public void test005() throws Exception {
        deploy("005.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> task -> task -> end
        assertEquals(11, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        start(key, "main", args);

        // ---

        verify(testTask, times(1)).call(eq("hello"));
        verify(testTask, times(1)).call(eq(testValue));
        verify(testTask, times(1)).call(anyMap());
        verify(testTask, times(1)).call(eq(1), eq(2));
    }

    @Test
    public void test006() throws Exception {
        deploy("006.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        TestTask testTask = spy(new TestTask("bStr", "cStr"));
        register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aStr", testValue);
        start(key, "main", args);

        // ---

        verify(testTask, times(1)).execute(any(ExecutionContext.class));
        verify(testBean, times(1)).toString(eq(testValue));
    }

    @Test
    public void test007() throws Exception {
        deploy("007.yml");

        ProcessDefinition pd = getDefinition("main");

        //            /-> task -\   /-> task -\
        // start -> gw -> task -> gw -> task -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("d"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test008() throws Exception {
        deploy("008.yml");

        ProcessDefinition pd = getDefinition("main");

        //            /-> task -> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test009() throws Exception {
        deploy("009.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> subprocess -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess: start -> task -> task -> end
        assertEquals(7, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

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

        ProcessDefinition pd = getDefinition("main");

        //                    /----------------------------->\
        // start -> task -> task + boundary-event -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

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

        ProcessDefinition pd = getDefinition("main");

        //            /----------------------------->\
        // start -> task + boundary-event -> task -> task -> end
        assertEquals(11, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        TestErrorTask testErrorTask = spy(new TestErrorTask());
        register("testErrorTask", testErrorTask);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

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

        ProcessDefinition pd = getDefinition("main");

        //                /------------------------------->\
        // start -> subprocess + boundary-event -> task -> task -> end
        assertEquals(11, pd.getChildren().size());
        // subprocess:
        // start -> task -> task -> end
        assertEquals(7, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(testBean, times(1)).throwBpmnError(anyString());
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test013() throws Exception {
        deploy("013.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> callactiviti -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test014() throws Exception {
        deploy("014.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /------------------->\
        // start -> gw -> callactivity -> end
        assertEquals(8, pd.getChildren().size());

        int loops = 100;

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("cnt", 0);
        args.put("loops", loops);
        start(key, "main", args);

        // ---

        verify(testBean, times(loops)).inc(anyInt());
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test015() throws Exception {
        deploy("015.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        String testValue = "test#" + System.currentTimeMillis();

        TestTask testTask = spy(new TestTask());
        register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("interpolation", testValue);
        start(key, "main", args);

        // ---

        String s = "multiline test with\nstring " + testValue;
        verify(testTask, times(1)).call(eq(s));
    }

    @Test
    public void test016() throws Exception {
        deploy("016.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> task -> end
        assertEquals(9, pd.getChildren().size());

        int inputNumber = ThreadLocalRandom.current().nextInt();
        boolean inputBoolean = ThreadLocalRandom.current().nextBoolean();
        String inputString = "test#" + System.currentTimeMillis();

        TestInterface testTask1 = mock(TestInterface.class);
        register("testTask1", testTask1);

        TestInterface testTask2 = spy(new TestInterface() {
            @Override
            @SuppressWarnings("unchecked")
            public void call(Object arg1) {
                List<Object> l = (List<Object>) arg1;
                assertEquals(inputNumber, l.get(0));
            }
        });
        register("testTask2", testTask2);

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
        register("testTask3", testTask3);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("inputNumber", inputNumber);
        args.put("inputBoolean", inputBoolean);
        args.put("inputString", inputString);
        start(key, "main", args);

        // ---

        verify(testTask1, times(1)).call(eq(inputNumber));
        verify(testTask2, times(1)).call(any());
        verify(testTask3, times(1)).call(any());
    }

    @Test
    public void test017() throws Exception {
        deploy("017.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> catchevent -> task -> end
        assertEquals(9, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
        reset(testBean);

        // ---

        resume(key, "ev1");

        verify(testBean, times(1)).toString(eq("b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test018() throws Exception {
        deploy("018.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("input", 10);
        start(key, "main", args);

        ArgumentCaptor<Object> ac = ArgumentCaptor.forClass(Object.class);
        verify(testBean, times(1)).toString(ac.capture());

        Object v = ac.getValue();
        assertTrue(v.equals(20) || v.equals(20.0)); // Oracle OpenJDK8 vs "post-Oracle" OpenJDKs quirks
    }

    @Test
    public void test019() throws Exception {
        deploy("019.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        verify(testBean, times(1)).toString(eq(12345));
    }

    @Test
    public void test020() throws Exception {
        deploy("020.yml");

        ProcessDefinition pd = getDefinition("main");

        // start -> task -> task -> end
        assertEquals(7, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        verify(testBean, times(1)).toString(eq(12345));
    }

    @Test
    public void test021() throws Exception {
        deploy("021.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);

        start(key, "main", args);

        // ---
        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test021_2() throws Exception {
        deploy("021_2.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        try {
            start(key, "main", args);
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

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test022_2() throws Exception {
        deploy("022_2.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        try {
            start(key, "main", args);
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

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 100);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test023_2() throws Exception {
        deploy("023.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /--> task ---------> end
        // start -> gw -> task -> task -> end
        assertEquals(13, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -100);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("c"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test024() throws Exception {
        deploy("024.yml");

        ProcessDefinition pd = getDefinition("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        // start -> task -> end
        assertEquals(5, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", Collections.emptyMap());

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test025() throws Exception {
        deploy("025.yml");

        ProcessDefinition pd = getDefinition("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /----------> end
        // start -> task -> gw -> task -> end
        assertEquals(11, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test025_2() throws Exception {
        deploy("025.yml");

        ProcessDefinition pd = getDefinition("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /----------> end
        // start -> task -> gw -> task -> end
        assertEquals(11, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("else"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test026() throws Exception {
        deploy("026.yml");

        ProcessDefinition pd = getDefinition("main");

        //               /-------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> task -> end-error
        // start -> task -> gw -> task -> end
        assertEquals(13, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("b"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test027() throws Exception {
        deploy("027.yml");

        ProcessDefinition pd = getDefinition("main");

        //             /--------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> end
        // start -> task -> gw -> end
        assertEquals(9, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test027_2() throws Exception {
        deploy("027.yml");

        ProcessDefinition pd = getDefinition("main");

        //             /--------------------------------->\
        // start -> subprocess + boundary-event -> task -> end
        assertEquals(9, pd.getChildren().size());
        // subprocess
        //                   /--> end
        // start -> task -> gw -> end
        assertEquals(9, findSubprocess(pd).getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = getDefinition("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", -1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028_2() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = getDefinition("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=1"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test028_3() throws Exception {
        deploy("028.yml");

        ProcessDefinition pd = getDefinition("main");

        //                         /--> task("success") -> end
        //                   /--> gw -> task("success=2") -> error-end
        // start -> task -> gw -> task("success=1") -> end
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test029() throws Exception {
        deploy("029.yml");

        ProcessDefinition pd = getDefinition("main");

        //                         /--> task("success")   ->\
        //                   /--> gw -> task("success=2") ->\
        // start -> task -> gw -> task("success=1")       -> task("success=end") -> end
        assertEquals(19, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("a"));
        verify(testBean, times(1)).toString(eq("success=2"));
        verify(testBean, times(1)).toString(eq("success=end"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test030() throws Exception {
        deploy("030.yml");

        ProcessDefinition pd = getDefinition("main");

        //           /--> task -> task ---------------->\
        //          /              /--> task -> task -->\
        // start -> gw -> task -> gw -> task ----------> end
        assertEquals(21, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("log", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("name", "foo");
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).info(eq("test"), eq("Hello, foo"));
        verify(testBean, times(1)).info(eq("test -- 3"), eq("Hello, foo"));
        verify(testBean, times(1)).info(eq("test -- 4"), eq("Hello, foo"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test031() throws Exception {
        deploy("031.yml");

        ProcessDefinition pd = getDefinition("main");

        //                          /--> task -->\
        // start -> subprocess + boundary-event -> end
        assertEquals(9, pd.getChildren().size());

        // subprocess
        // start -> callactiviti -> end
        assertEquals(5, findSubprocess(pd).getChildren().size());

        pd = getDefinition("myOtherFlow");

        // callactiviti
        // start -> task -> end
        assertEquals(5, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("in-call-activiti"));
        verify(testBean, times(1)).toString(eq("e"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test032() throws Exception {
        deploy("032.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        register("vars", testBean);
        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("aInt", 2);
        start(key, "main", args);

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
        register("docker", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("workDir", "/tmp");
        args.put("txId", txId);
        start(key, "main", args);

        // ---

        ArgumentCaptor<ExecutionContext> captor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task, times(1)).execute(captor.capture());

        ExecutionContext ctx = captor.getValue();

        Map<String, Object> env = (Map<String, Object>) ctx.getVariable("env");
        assertNotNull(env);
        assertEquals(2, env.size());
        assertEquals(123, env.get("x"));
        assertEquals(txId, env.get("y"));

        List<String> opts = (List<String>) ctx.getVariable("hosts");
        assertNotNull(opts);
        assertEquals(2, opts.size());
        assertEquals("foo:10.0.0.3", opts.get(0));
    }

    @Test
    public void test034() throws Exception {
        deploy("034.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", new HashMap<>());
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(isNull());
    }

    @Test
    public void test035() throws Exception {
        deploy("035.yml");

        JavaDelegate task = spy(new JavaDelegate() {
            @SuppressWarnings("rawtypes")
            @Override
            public void execute(ExecutionContext ctx) {
                Object o = ctx.getVariable("aList");
                assertTrue(o instanceof List);

                List l = (List) o;
                assertEquals(3, l.size());
                assertEquals(132, l.get(2));
            }
        });
        register("testTask", task);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(task, times(1)).execute(any(ExecutionContext.class));
    }

    @Test
    public void test036() throws Exception {
        deploy("036.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", 1L);
        start(key, "main", args);

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
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("1"));
        verify(testBean, times(1)).toString(eq("3"));
    }

    @Test
    public void test040() throws Exception {
        deploy("040.yml");

        ProcessDefinition pd = getDefinition("main");
        //                   /--> taskA1 -> taskA2 -\
        // start -> task -> gw -> taskB ------------> end
        //                   \--> taskD ------------/
        assertEquals(17, pd.getChildren().size());

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "a");
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do a"));
        verify(testBean, times(1)).toString(eq("do a2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test040_2() throws Exception {
        deploy("040.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "b");
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do b"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test040_3() throws Exception {
        deploy("040.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "123");
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do default"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test041() throws Exception {
        deploy("041.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 1);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("do 1"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test042() throws Exception {
        deploy("042.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 42);
        start(key, "main", args);

        verify(testBean, times(1)).toString(eq("after switch/case"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test043() throws Exception {
        deploy("043.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", 2);
        args.put("caseValue1", 10);
        args.put("caseValue2", 2);
        start(key, "main", args);

        verify(testBean, times(1)).toString(eq("do 2"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test044() throws Exception {
        deploy("044.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("switchValue", "default");
        start(key, "main", args);

        verify(testBean, times(1)).toString(eq("do default as string"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test045() throws Exception {
        deploy("045.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("err"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test046() throws Exception {
        deploy("046.yml");

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);
    }

    @Test
    public void test047() throws Exception {
        deploy("047.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        verify(testBean, times(1)).toString(eq("myFlowA"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test048() throws Exception {
        deploy("048.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        JavaDelegate task = mock(JavaDelegate.class);
        doThrow(new BpmnError("first error"))
                .doNothing()
                .when(task).execute(any());

        register("testErrorTask", task);

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        // ---
        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---
        ArgumentCaptor<ExecutionContext> ctxCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task, times(2)).execute(ctxCaptor.capture());

        List<ExecutionContext> retryCtx = ctxCaptor.getAllValues();
        assertEquals("test", retryCtx.get(0).getVariable("msg"));
        assertEquals("retry", retryCtx.get(1).getVariable("msg"));

        Map<String, String> expectedVars0 = new HashMap<>();
        expectedVars0.put("a", "a-value-original");
        expectedVars0.put("b", "b-value-original");

        Map<String, String> expectedVars1 = new HashMap<>();
        expectedVars1.put("a", "a-value-original");
        expectedVars1.put("b", "b-value-retry");

        assertEquals(expectedVars0, retryCtx.get(0).getVariable("nested"));
        assertEquals(expectedVars1, retryCtx.get(1).getVariable("nested"));

        verify(testBean, times(1)).toString(eq("end"));

        verifyNoMoreInteractions(testBean);
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test049() throws Exception {
        deploy("049.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", 1L);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq(101L));
        verify(testBean, times(1)).toString(eq("2:item1"));
        verify(testBean, times(1)).toString(eq("2:2"));
        verify(testBean, times(1)).toString(eq("2:item3"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test050() throws Exception {
        deploy("050.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        List<String> arr = Arrays.asList("item1", "item2", "item3");
        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", arr);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq(arr));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test051() throws Exception {
        deploy("051.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        List<String> arr = Arrays.asList("item1", "item2", "item3");

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", arr);
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("single1"));
        verify(testBean, times(1)).toString(eq(arr));
        verify(testBean, times(1)).toString(eq("singleLast"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test052() throws Exception {
        deploy("052.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", Arrays.asList("item1", "item2", "item3"));
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("item1"));
        verify(testBean, times(1)).toString(eq("item2"));
        verify(testBean, times(1)).toString(eq("item3"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test053() throws Exception {
        deploy("053.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", Arrays.asList("item1", "item2", "item3"));
        start(key, "main", args);

        // ---

        verify(testBean, times(1)).toString(eq("testuser1:wheel"));
        verify(testBean, times(1)).toString(eq("testuser2:root"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test054() throws Exception {
        deploy("054.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        start(key, "main", args);

        // ---

        verify(task, times(3)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello red"));
        verify(task, times(1)).log(eq("hello green"));
        verify(task, times(1)).log(eq("hello blue"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test055() throws Exception {
        deploy("055.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("x", Arrays.asList("item1", "item2"));
        start(key, "main", args);

        // ---

        verify(task, times(3)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello red"));
        verify(task, times(1)).log(eq("hello [item1, item2]"));
        verify(task, times(1)).log(eq("hello blue"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test056() throws Exception {
        deploy("056.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", Arrays.asList("item1", "item2"));
        start(key, "main", args);

        // ---

        verify(task, times(1)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello [item1, item2]"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test057() throws Exception {
        deploy("057.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", Arrays.asList("item1", "item2"));
        start(key, "main", args);

        // ---

        verify(task, times(2)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello item1"));
        verify(task, times(1)).log(eq("hello item2"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test058() throws Exception {
        deploy("058.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        JavaDelegate task = mock(JavaDelegate.class);
        doThrow(new BpmnError("first error"))
                .doNothing()
                .when(task).execute(any());

        register("testErrorTask", task);

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---
        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---
        ArgumentCaptor<ExecutionContext> ctxCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task, times(3)).execute(ctxCaptor.capture());

        List<ExecutionContext> retryCtx = ctxCaptor.getAllValues();
        assertEquals("test: item1", retryCtx.get(0).getVariable("msg"));
        assertEquals("retry: item1", retryCtx.get(1).getVariable("msg"));
        assertEquals("test: item2", retryCtx.get(2).getVariable("msg"));

        verify(testBean, times(1)).toString(eq("end"));

        verifyNoMoreInteractions(testBean);
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test059() throws Exception {
        deploy("059.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myArray", Arrays.asList("a1", "a2", "a3"));
        start(key, "main", args);

        // ---
        verify(task, times(6)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello a1"));
        verify(task, times(1)).log(eq("hello a2"));
        verify(task, times(1)).log(eq("hello a3"));

        verify(task, times(1)).log(eq("hello from call item1"));
        verify(task, times(1)).log(eq("hello from call item2"));
        verify(task, times(1)).log(eq("hello from call item3"));

        verifyNoMoreInteractions(task);
    }

    @Test
    public void test060() throws Exception {
        deploy("060.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("nullVariable", null);

        start(key, "main", args);

        // ---

        verify(task, times(0)).execute(any(ExecutionContext.class));
        verify(task, times(0)).log(eq("hello a1"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test061() {
        deploy("061.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("notArrayVariable", "invalid variable type");

        try {
            start(key, "main", args);
            fail("exception expected");
        } catch (ExecutionException e) {
            assertTrue(e.getCause().getCause().getMessage().contains("should be a list"));
        }
    }

    @Test
    public void test062() throws Exception {
        deploy("062.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("calledFlow", "myFlow1");

        start(key, "main", args);

        verify(testBean, times(1)).toString(eq("from MyFlow1"));
        verifyNoMoreInteractions(testBean);
    }

    @Test
    public void test063() throws Exception {
        deploy("063.yml");

        JavaDelegate checkpointTask = spy(new JavaDelegate() {
            @Override
            public void execute(ExecutionContext ctx) {
                ctx.setVariable("checkpointId", "123");
            }
        });
        register("checkpoint", checkpointTask);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        verify(checkpointTask, times(1)).execute(any());
        verifyNoMoreInteractions(checkpointTask);
    }

    @Test
    public void test064() throws Exception {
        /*
        Variables provided to withItems may be null. The Task should only execute in cases where the variable provided
        to withItems is non-null
         */
        deploy("064.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("nullVariable", null); // should't execute the task
        List<String> theList = new ArrayList<>(3);
        theList.add("hello");
        theList.add(null);
        theList.add("world");
        args.put("listWithNull", theList);
        theList = new ArrayList<>(2);
        theList.add("Hello");
        theList.add("World");
        args.put("fineList", theList);

        start(key, "main", args);

        // ---

        verify(task, times(5)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("None of these are null -> Hello"));
        verify(task, times(1)).log(eq("None of these are null -> World"));

        verify(task, times(1)).log(eq("It's okay to have a null item -> hello"));
        verify(task, times(1)).log(eq("It's okay to have a null item -> "));
        verify(task, times(1)).log(eq("It's okay to have a null item -> world"));

        verifyNoMoreInteractions(task);
    }

    @Test
    public void test065() throws Exception {
        deploy("065.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);
        // ---

        String key = UUID.randomUUID().toString();

        start(key, "default", Collections.emptyMap());

        // ---

        verify(task, times(6)).execute(any(ExecutionContext.class));

        verify(task, times(1)).log(eq("A 0"));
        verify(task, times(1)).log(eq("A 1"));

        verify(task, times(1)).log(eq("B 0"));
        verify(task, times(1)).log(eq("B 1"));

        verify(task, times(1)).log(eq("C 0"));
        verify(task, times(1)).log(eq("C 1"));

        verifyNoMoreInteractions(task);
    }

    @Test
    public void test066() throws Exception {
        deploy("066.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);
        // ---

        String key = UUID.randomUUID().toString();

        start(key, "default", Collections.singletonMap("nestedWithItems", Arrays.asList("0", null, "2", "3")));

        // ---

        verify(task, times(12)).execute(any(ExecutionContext.class));

        verify(task, times(1)).log(eq("A 0"));
        verify(task, times(1)).log(eq("A "));
        verify(task, times(1)).log(eq("A 2"));
        verify(task, times(1)).log(eq("A 3"));

        verify(task, times(1)).log(eq("B 0"));
        verify(task, times(1)).log(eq("B "));
        verify(task, times(1)).log(eq("B 2"));
        verify(task, times(1)).log(eq("B 3"));

        verify(task, times(1)).log(eq("C 0"));
        verify(task, times(1)).log(eq("C "));
        verify(task, times(1)).log(eq("C 2"));
        verify(task, times(1)).log(eq("C 3"));

        verifyNoMoreInteractions(task);
    }

    @Test
    public void test067() throws Exception {
        deploy("067.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("myLogger", task);
        register("myTask", new JavaDelegate() {
            @Override
            public void execute(ExecutionContext ctx) {
                ctx.setVariable("var", ctx.getVariable("message"));
            }
        });
        // ---

        String key = UUID.randomUUID().toString();

        start(key, "default", Collections.singletonMap("nestedWithItems", Arrays.asList("0", "1", "2")));

        // ---

        verify(task, times(1)).log(eq(
                Arrays.asList(
                        Arrays.asList("A 0", "A 1", "A 2"),
                        Arrays.asList("B 0", "B 1", "B 2"),
                        Arrays.asList("C 0", "C 1", "C 2"))));
    }

    @Test
    public void test068() throws Exception {
        deploy("068.yml");

        MyLogger logBean = spy(new MyLogger());
        register("log", logBean);

        JavaDelegate task = mock(JavaDelegate.class);
        doThrow(new BpmnError("first error"))
                .doNothing()
                .when(task).execute(any());

        register("testErrorTask", task);

        JavaDelegate task2 = mock(JavaDelegate.class);
        doThrow(new BpmnError("first error"))
                .doNothing()
                .when(task2).execute(any());

        register("testErrorTask2", task2);

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        // ---
        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---
        ArgumentCaptor<ExecutionContext> ctxCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task, times(2)).execute(ctxCaptor.capture());

        List<ExecutionContext> retryCtx = ctxCaptor.getAllValues();
        assertEquals("test", retryCtx.get(0).getVariable("msg"));
        assertEquals("retry", retryCtx.get(1).getVariable("msg"));

        verifyNoMoreInteractions(task);

        // ---
        ArgumentCaptor<ExecutionContext> ctxCaptor2 = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task2, times(2)).execute(ctxCaptor2.capture());

        List<ExecutionContext> retryCtx2 = ctxCaptor2.getAllValues();
        assertEquals("test2", retryCtx2.get(0).getVariable("msg"));
        assertEquals("retry2", retryCtx2.get(1).getVariable("msg"));

        verifyNoMoreInteractions(task2);
    }

    @Test
    public void test069() throws Exception {
        deploy("069.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", new HashMap<>());

        // ---

        verify(task, times(3)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello a"));
        verify(task, times(1)).log(eq("hello b"));
        verify(task, times(1)).log(eq("hello c"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test070() throws Exception {
        deploy("070.yml");

        MyLogger task = spy(new MyLogger());
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("log", task);
        // ---

        String key = UUID.randomUUID().toString();

        start(key, "default", null);

        // ---

        verify(task, times(12)).call(any());

        verify(task, times(1)).log(eq("Something: [apple, orange, bannana], 0"));
        verify(task, times(1)).log(eq("Something else: apple"));
        verify(task, times(1)).log(eq("Something else: orange"));
        verify(task, times(1)).log(eq("Something else: bannana"));

        verify(task, times(1)).log(eq("Something: [dog, cat, mouse], 1"));
        verify(task, times(1)).log(eq("Something else: dog"));
        verify(task, times(1)).log(eq("Something else: cat"));
        verify(task, times(1)).log(eq("Something else: mouse"));

        verify(task, times(1)).log(eq("Something: [one, two, three], 2"));
        verify(task, times(1)).log(eq("Something else: one"));
        verify(task, times(1)).log(eq("Something else: two"));
        verify(task, times(1)).log(eq("Something else: three"));

        verifyNoMoreInteractions(task);
    }

    @Test
    public void test071() throws Exception {
        deploy("071.yml");

        MyLogger task = spy(new MyLogger());
        register("myLogger", task);
        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());

        // ---
        Map<String, String> items = new HashMap<>();
        items.put("k1", "v1");
        items.put("k2", "v2");

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = new HashMap<>();
        args.put("myMap", items);
        start(key, "main", args);

        // ---

        verify(task, times(2)).execute(any(ExecutionContext.class));
        verify(task, times(1)).log(eq("hello k1 -> v1"));
        verify(task, times(1)).log(eq("hello k2 -> v2"));
        verifyNoMoreInteractions(task);
    }

    @Test
    public void test072() throws Exception {
        deploy("072.yml");

        MyLogger task = spy(new MyLogger());
        register("log", task);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.emptyMap();
        start(key, "main", args);

        // ---

        verify(task, times(3)).log(eq("flow: main"));
        verify(task, times(1)).log(eq("flow: myFlow"));
        verify(task, times(1)).log(eq("flow: myFlow2"));
        verify(task, times(1)).log(eq("flow: myFaultyFlow"));
        verify(task, times(1)).log(eq("error handler: flow: main"));
    }

    @Test
    public void test073() throws Exception {
        deploy("073.yml");

        MyLogger task = spy(new MyLogger());
        register("log", task);

        TestBean testBean = spy(new TestBean());
        register("vars", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        Map<String, Object> args = Collections.singletonMap("nested", 123);
        start(key, "main", args);

        // ---

        verify(task, times(2)).log(eq("Hello, 123"));
    }

    @Test
    public void test074() throws Exception {
        deploy("074.yml");

        register("__withItemsUtils", new StepConverter.WithItemsUtilsTask());
        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        MyLogger task = spy(new MyLogger());
        register("log", task);

        TestErrorTask http = spy(new TestErrorTask());
        register("http", http);

        // ---

        String key = UUID.randomUUID().toString();

        start(key, "default");

        // ---
        ArgumentCaptor<ExecutionContext> captor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(http, times(12)).execute(captor.capture());

        List<String> urls = captor.getAllValues()
                .stream()
                .map(e -> (String) e.getVariable("url"))
                .collect(Collectors.toList());

        assertContains("https://nonexistant.example.com/test/a", urls, 4);
        assertContains("https://nonexistant.example.com/test/b", urls, 4);
        assertContains("https://nonexistant.example.com/test/c", urls, 4);

        assertTrue(urls.isEmpty());

        verifyNoMoreInteractions(http);
    }

    @Test
    public void test075() throws Exception {
        deploy("075.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        try {
            start(key, "default");
            fail("should fail");
        } catch (ExecutionException e) {
            // do nothing
        }

        // ---

        verify(testBean, times(3)).throwBpmnError(anyString());
    }

    @Test
    public void test076() {
        deploy("076.yml");

        MyLogger log = spy(new MyLogger());
        register("log", log);

        register("throw", new ThrowExceptionTask());

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        // ---

        String key = UUID.randomUUID().toString();
        try {
            start(key, "default");
            fail("should fail");
        } catch (ExecutionException e) {
            // do nothing
        }

        // ---

        verify(log, times(2)).log(eq("Here's calledFlow"));
        verify(log, times(2)).log(eq("Here's nestedFlow called by calledFlow"));
        verify(log, times(2)).log(eq("Error NOW!"));
    }

    @Test
    public void test077() throws Exception {
        deploy("077.yml");

        ProcessDefinition pd = getDefinition("main");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        JavaDelegate task = mock(JavaDelegate.class);
        doThrow(new BpmnError("first error"))
                .doNothing()
                .when(task).execute(any());

        register("testErrorTask", task);

        register("__retryUtils", new YamlTaskStepConverter.RetryUtilsTask());

        // ---
        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---
        ArgumentCaptor<ExecutionContext> ctxCaptor = ArgumentCaptor.forClass(ExecutionContext.class);
        verify(task, times(2)).execute(ctxCaptor.capture());

        List<ExecutionContext> retryCtx = ctxCaptor.getAllValues();
        assertEquals("test", retryCtx.get(0).getVariable("msg"));
        assertEquals("retry", retryCtx.get(1).getVariable("msg"));

        verify(testBean, times(1)).toString(eq("end"));

        verifyNoMoreInteractions(testBean);
        verifyNoMoreInteractions(task);
    }

    // FORMS (100 - 199)

    @Test
    public void test100() throws Exception {
        deploy("100.yml");

        String formValue = "test#" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        verify(testBean, times(1)).toString(eq("aaa"));

        // ---

        UUID formId = getFirstFormId();
        Map<String, Object> data = Collections.singletonMap("name", formValue);
        submitForm(formId, data);

        verify(testBean, times(1)).toString(eq(formValue));
    }

    @Test
    public void test101() throws Exception {
        deploy("101.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("age", 256));
        assertFalse(result.isValid());

        Map<String, Object> data = new HashMap<>();
        data.put("age", 64);
        data.put("percent", -1.0);

        result = submitForm(formId, data);
        assertFalse(result.isValid());

        result = submitForm(formId, Collections.singletonMap("age", 64));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq(64));
    }

    @Test
    public void test102() throws Exception {
        deploy("102.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        int[] numbers = new int[]{-1, 5, 98};

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("favouriteNumbers", numbers));
        assertFalse(result.isValid());

        numbers = new int[]{0, 5, 98};

        result = submitForm(formId, Collections.singletonMap("favouriteNumbers", numbers));
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
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", Collections.singletonMap("inputValue", inputValue));

        // ---

        UUID formId = getFirstFormId();
        Form f = getForm(formId);

        Map<String, Object> data = (Map<String, Object>) f.getEnv().get("myForm");
        FormSubmitResult result = submitForm(formId, data);
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
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        Map<String, Object> data = Collections.singletonMap("testValue", valueB);
        FormSubmitResult result = submitForm(formId, data);
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
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.emptyMap());
        assertFalse(result.isValid());

        verify(testBean, never()).toString(any());
    }

    @Test
    public void test106() throws Exception {
        deploy("106.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("testValue", "d"));
        assertFalse(result.isValid());

        result = submitForm(formId, Collections.singletonMap("testValue", "a"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("a"));
    }

    @Test
    public void test107() throws Exception {
        deploy("107.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("testValue", "else"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("else"));
    }

    @Test
    public void test108() throws Exception {
        deploy("108.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("testValue", "else"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("else"));
    }

    @Test
    public void test109() throws Exception {
        deploy("109.yml");

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();

        FormSubmitResult result = submitForm(formId, Collections.singletonMap("testValue", "else"));
        assertTrue(result.isValid());

        verify(testBean, times(1)).toString(eq("else"));
    }

    @Test
    public void test110() throws Exception {
        deploy("110.yml");

        String formValue = "test#" + System.currentTimeMillis();

        // ---

        TestBean testBean = spy(new TestBean());
        register("testBean", testBean);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main", null);

        // ---

        UUID formId = getFirstFormId();
        Map<String, Object> data = Collections.singletonMap("myField", formValue);
        submitForm(formId, data);

        verify(testBean, times(1)).toString(eq(formValue));
    }

    @Test
    public void testJunk() {
        deploy("junk.yml");
    }

    // MISC

    @Test
    public void testOld() {
        assertThrows(RuntimeException.class, () -> deploy("old.yml"));
    }


    @Test
    public void test113() throws Exception {
        deploy("113.yml");

        MyLogger logger = spy(new MyLogger());
        register("myLogger", logger);

        TestTaskWithResume testTask = spy(new TestTaskWithResume());
        register("testTask", testTask);

        // ---

        String key = UUID.randomUUID().toString();
        start(key, "main");

        resume(key, "myEvent");

        resume(key, "myEvent");

        // ---
        verify(logger, times(1)).log(eq("RESUME1"));
        verify(logger, times(1)).log(eq("RESUME2"));
    }

    private ProcessDefinition findSubprocess(ProcessDefinition pd) {
        return pd.getChildren().stream()
                .filter(e -> e instanceof ProcessDefinition)
                .map(e -> (ProcessDefinition) e)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("subprocess not found"));
    }

    private static void assertContains(String str, List<String> items, int expectedCount) {
        for (int i = 0; i < expectedCount; i++) {
            boolean removed = items.remove(str);
            assertTrue(removed);
        }

        assertFalse(items.contains(str));
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
            vars.forEach((k, v) -> {
                Object vv = executionContext.interpolate(v);
                executionContext.setVariable(k, vv);
            });
        }
    }

    private static class MyLogger implements JavaDelegate {

        @Override
        public void execute(ExecutionContext executionContext) {
            log(executionContext.getVariable("message"));
        }

        public void log(Object message) {
            System.out.println(message);
        }

        public void call(Object message) {
            log(message);
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
        public void execute(ExecutionContext ctx) {
            if (srcVar != null && dstVar != null) {
                ctx.setVariable(dstVar, ctx.getVariable(srcVar));
            }
        }

        public void call(Object arg1) {
        }

        public void call(Object arg1, Object arg2) {
        }
    }

    private static class TestTaskWithResume implements JavaDelegate {

        public static final String SUSPEND_MARKER = "TestTaskWithResumeSuspend";

        @Override
        public void execute(ExecutionContext ctx) {
            Object in = ctx.getVariable("num");

            if (ctx.getVariable(SUSPEND_MARKER) != null && (Boolean) ctx.getVariable(SUSPEND_MARKER)) {
                ctx.setVariable("TestTaskWithResumeResult", "RESUME" + in);
            } else {
                ctx.setVariable(SUSPEND_MARKER, true);
                ctx.suspend("myEvent", null, true);

                ctx.setVariable("TestTaskWithResumeResult", "SUSPEND" + in);
            }
        }
    }

    private static class DockerTask implements JavaDelegate {

        @Override
        public void execute(ExecutionContext ctx) throws Exception {
        }
    }

    private interface TestInterface {

        void call(Object arg1);
    }

    private static class TestErrorTask implements JavaDelegate {

        @Override
        public void execute(ExecutionContext ctx) {
            throw new BpmnError("boom!");
        }
    }

    public class ThrowExceptionTask implements Task {

        public void call(Object o) throws Exception {
            if (o instanceof Exception) {
                throw (Exception) o;
            } else if (o instanceof String) {
                throw new RuntimeException(o.toString());
            } else if (o instanceof Serializable) {
                throw new RuntimeException("Process error:" + o);
            } else {
                throw new RuntimeException(o != null ? o.toString() : "n/a");
            }
        }
    }
}
