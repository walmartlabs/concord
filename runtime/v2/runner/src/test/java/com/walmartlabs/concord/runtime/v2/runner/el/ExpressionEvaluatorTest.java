package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vars.GlobalVariablesImpl;
import com.walmartlabs.concord.runtime.v2.sdk.GlobalVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.junit.Test;

import java.io.Serializable;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ExpressionEvaluatorTest {

    @Test
    public void testEva() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        GlobalVariables vars = new GlobalVariablesImpl(Collections.singletonMap("name", "${Concord}"));

        String str = ee.eval(new DummyContext(vars), "Hello ${name}", String.class);
        assertEquals("Hello ${Concord}", str);
    }

    @Test
    public void testEvalMap() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        GlobalVariables vars = new GlobalVariablesImpl(Collections.singletonMap("name", "${Concord}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("msg", "Hello, ${name}");
        input.put("text", "${msg}");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("msg", "Hello, ${Concord}");
        expected.put("text", "Hello, ${Concord}");

        Map<String, Object> output = ee.evalAsMap(new DummyContext(vars), input);
        assertThat(output, is(expected));
    }

    @Test
    public void testEvalList() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        GlobalVariables vars = new GlobalVariablesImpl(Collections.singletonMap("name", "${Concord}"));

        List<String> input = new ArrayList<>();
        input.add("Hello, ${name}");

        List<String> expected = new ArrayList<>();
        expected.add( "Hello, ${Concord}");

        List<String> output = ee.evalAsList(new DummyContext(vars), input);
        assertThat(output, is(expected));
    }

    @Test
    public void testEval1() {
        /*
         * configuration:
         *   arguments:
         *     x: ${y}
         *     z: ${y.y1}
         *     y:
         *     	y1: ${task(..)}
         *     	y2: "asdasd"
         *     	y3: ${z}
         */

        Map<Object, Object> input = map(
                "x", "${y}",
                    "z", "${y.y1}",
                    "y", map(
                "y1", "task",
                        "y2", "abc",
                        "y3", "${z}"));

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);

        Map<Object, Object> y = map("y1", "task", "y2", "abc", "y3", "task");
        assertThat(output, is(map("x", y,
                "z","task",
                "y", y)));
    }

    @Test
    public void testEval2() {
        Map<Object, Object> input = map(
                "x", "${y}",
                "y", "${x}");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        try {
            ee.evalAsMap(new DummyContext(vars), input);
            fail("exception expected");
        } catch (RuntimeException e) {
            assertEquals("Key 'x' already in evaluation", e.getMessage());
        }
    }

    @Test
    public void testEval3() {
        /*
         * configuration:
         *   arguments:
         *     x: ${y}
         *     z: ${y.y1}
         *     y:
         *     	y1: ${task(y.y2)}
         *     	y2: "asdasd"
         *     	y3: ${z}
         */
        Map<Object, Object> input = map(
                "x", "${y}",
                "z", "${y.y1}",
                "y", map(
                        "y1", "${task.foo(y.y2)}",
                        "y2", "abc",
                        "y3", "${z}"));

        TaskProviders providers = mock(TaskProviders.class);
        TestTask task = spy(new TestTask());
        when(providers.createTask(any(), eq("task"))).thenReturn(task);

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(providers);
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);

        Map<Object, Object> y = map("y1", "from-task: abc", "y2", "abc", "y3", "from-task: abc");
        assertThat(output, is(map("x", y,
                "z","from-task: abc",
                "y", y)));

        verify(task, times(1)).foo(anyString());
    }

    @Test
    public void testEval4() {
        /*
         * configuration:
         *   arguments:
         *     x: ${y}
         *     z: ${y.y1}
         *     y:
         *     	y1: ${task(y.y2)}
         *     	y2: "asdasd"
         *     	y3: ${z}
         */
        Map<Object, Object> input = map(
                "x", "${y}",
                "z", "${y.y1}",
                "y", map(
                        "y1", "${task.foo(y.y2)}",
                        "y2", "abc",
                        "y3", "${z}"));

        TaskProviders providers = mock(TaskProviders.class);
        TestTask2 task = spy(new TestTask2());
        when(providers.createTask(any(), eq("task"))).thenReturn(task);

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(providers);
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);

        Map<Object, Object> y = map("y1", "${abc}", "y2", "abc", "y3", "${abc}");
        assertThat(output, is(map("x", y,
                "z","${abc}",
                "y", y)));

        verify(task, times(1)).foo(anyString());
    }

    @Test
    public void testEval5() {
        Map<Object, Object> input = map(
                "y", map(
                        "y1", "y1-value",
                        "y2", "${y1}"));

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        try {
            Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Can't find a variable in '${y1}'"));
        }
    }

    @Test
    public void testEval6() {
        Map<Object, Object> input = map(
                "x", Collections.singletonList("${y}"),
                        "y", "abc");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);

        assertThat(output, is(map("x", Collections.singletonList("abc"),
                "y","abc")));
    }

    @Test
    public void testEval7() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("x", "x-value");
        m.put("y", "${x}");
        List<Object> input = Arrays.asList("a", m);

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        List<Object> output = ee.evalAsList(new DummyContext(vars), input);

        assertThat(output, is(Arrays.asList("a", map("x", "x-value", "y", "x-value"))));
    }

    @Test
    public void testEval8() {
        /**
         * x:
         *   - ${y}
         * y: "abc"
         */

        Map<Object, Object> input = map("x", Collections.singletonList("${y}"), "y", "abc");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<Object, Object> output = ee.evalAsMap(new DummyContext(vars), input);

        assertThat(output, is(map("x", Collections.singletonList("abc"), "y", "abc")));
    }

    private static Map<Object, Object> map(Object ... values) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i+=2) {
            Object k = values[i];
            Object v = values[i + 1];
            result.put(k, v);
        }
        return result;
    }

    public static class TestTask implements Task {

        public Serializable foo(String value) {
            return "from-task: " + value;
        }
    }

    public static class TestTask2 implements Task {

        public Serializable foo(String value) {
            return "${" + value + "}";
        }
    }
}
