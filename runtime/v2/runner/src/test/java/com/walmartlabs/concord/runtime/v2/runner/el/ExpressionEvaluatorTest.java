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
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ExpressionEvaluatorTest {

    @Test
    public void testEvaGlobal() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.singletonMap("name", "${Concord}");

        // ---
        String str = ee.eval(global(vars), "Hello ${name}", String.class);
        assertEquals("Hello ${Concord}", str);
    }

    @Test
    public void testStrict() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.singletonMap("name", "Concord");
        Map<String, Object> strict = Collections.singletonMap("name", "Concord!!!");

        EvalContext ctx = new EvalContextFactoryImpl().strict(new SingleFrameContext(vars), strict);

        // ---
        String str = ee.eval(ctx, "Hello ${name}", String.class);
        assertEquals("Hello Concord!!!", str);
    }

    @Test
    public void testStrictUndef() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.singletonMap("name", "Concord");
        Map<String, Object> strict = Collections.emptyMap();

        EvalContext ctx = new EvalContextFactoryImpl().strict(new SingleFrameContext(vars), strict);

        // ---
        try {
            ee.eval(ctx, "Hello ${name}", String.class);
            fail("exception expected");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("variable 'name' used in 'Hello ${name}'"));
        }

        // undef as null
        // ---
        String str = ee.eval(undefAsNull(ctx), "Hello ${name}", String.class);
        assertNull(str);
    }

    @Test
    public void testEvalScope() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        Map<String, Object> vars = Collections.singletonMap("name", "${Concord}");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("msg", "Hello, ${name}");
        input.put("text", "${msg}");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("msg", "Hello, ${Concord}");
        expected.put("text", "Hello, ${Concord}");

        Map<String, Object> output = ee.evalAsMap(scope(vars), input);
        assertThat(output, is(expected));
    }

    @Test
    public void testEvalListGlobal() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        Map<String, Object> vars = Collections.singletonMap("name", "${Concord}");

        List<String> input = new ArrayList<>();
        input.add("Hello, ${name}");

        List<String> expected = new ArrayList<>();
        expected.add("Hello, ${Concord}");

        List<String> output = ee.evalAsList(global(vars), input);
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
                        "y1", "${in}",
                        "y2", "abc",
                        "y3", "${z}"));

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.singletonMap("in", "task");

        // scope -> ok
        // ---
        Map<Object, Object> output = ee.evalAsMap(scope(vars), input);

        Map<Object, Object> y = map("y1", "task", "y2", "abc", "y3", "task");
        assertThat(output, is(map("x", y,
                "z", "task",
                "y", y)));

        // global -> error (y undefined)
        // ---
        try {
            ee.evalAsMap(global(vars), input);
            fail("exception expected");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("variable 'y' used in '${y}'"));
        }

        // undef -> x = null, z = null, y ...y3 = null
        // ---
        output = ee.evalAsMap(undefAsNull(global(vars)), input);

        y.put("y3", null);
        assertThat(output, is(map("x", null,
                "z", null,
                "y", y)));
    }

    @Test
    public void testEval2() {
        Map<Object, Object> input = map(
                "x", "${y}",
                "y", "${x}");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.emptyMap();

        // global: x -> undefined
        // ---
        try {
            ee.evalAsMap(global(vars), input);
            fail("exception expected");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("variable 'y' used in '${y}'"));
        }

        // scope
        // ---
        try {
            ee.evalAsMap(scope(vars), input);
            fail("exception expected");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("variable 'x' used in '${x}'"));
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
        Map<String, Object> vars = Collections.emptyMap();

        // global: y -> undefined
        // ---
        try {
            ee.evalAsMap(global(vars), input);
            fail("exception expected");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("variable 'y' used in '${y}'"));
        }

        verify(task, times(0)).foo(anyString());

        // scope:
        // ---
        Map<Object, Object> output = ee.evalAsMap(scope(vars), input);

        Map<Object, Object> y = map("y1", "from-task: abc", "y2", "abc", "y3", "from-task: abc");
        assertThat(output, is(map("x", y,
                "z", "from-task: abc",
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
        Map<String, Object> vars = Collections.emptyMap();

        // scope:
        // ---
        Map<Object, Object> output = ee.evalAsMap(scope(vars), input);

        Map<Object, Object> y = map("y1", "${abc}", "y2", "abc", "y3", "${abc}");
        assertThat(output, is(map("x", y,
                "z", "${abc}",
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
        Map<String, Object> vars = Collections.emptyMap();

        try {
            ee.evalAsMap(scope(vars), input);
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("variable 'y1' used in '${y1}'"));
        }
    }

    @Test
    public void testEval6() {
        Map<Object, Object> input = map(
                "x", Collections.singletonList("${y}"),
                "y", "abc");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.emptyMap();

        // scope:
        // ---
        Map<Object, Object> output = ee.evalAsMap(scope(vars), input);

        assertThat(output, is(map("x", Collections.singletonList("abc"),
                "y", "abc")));
    }

    @Test
    public void testEval8() {
        /*
         * x:
         *   - ${y}
         * y: "abc"
         */
        Map<Object, Object> input = map("x", Collections.singletonList("${y}"), "y", "abc");

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> vars = Collections.emptyMap();

        // scope:
        // ---
        Map<Object, Object> output = ee.evalAsMap(scope(vars), input);

        assertThat(output, is(map("x", Collections.singletonList("abc"), "y", "abc")));
    }

    @Test
    public void testEvalHasVariable() {
        String str = "${hasVariable('x')}";

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        // ---

        boolean result = ee.eval(global(Collections.emptyMap()), str, Boolean.class);
        assertFalse(result);

        // ---

        Map<String, Object> vars = Collections.singletonMap("x", "x-value");
        result = ee.eval(global(vars), str, Boolean.class);
        assertTrue(result);
    }

    @Test
    public void testAllVariables() {
        String str = "${allVariables()}";

        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        // ---
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", Collections.singletonList("b"));
        vars.put("b", "bb");

        Map<String, Object> result = ee.evalAsMap(global(vars), str);
        assertEquals(vars, result);
    }

    private static EvalContext global(Map<String, Object> vars) {
        return new EvalContextFactoryImpl().global(new SingleFrameContext(vars));
    }

    private static EvalContext scope(Map<String, Object> vars) {
        return new EvalContextFactoryImpl().scope(new SingleFrameContext(vars));
    }

    private static EvalContext undefAsNull(EvalContext ctx) {
        return EvalContext.builder().from(ctx)
                .undefinedVariableAsNull(true)
                .build();
    }

    private static Map<Object, Object> map(Object... values) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            Object k = values[i];
            Object v = values[i + 1];
            result.put(k, v);
        }
        return result;
    }

    public static class TestTask implements Task {

        @SuppressWarnings("UnusedReturnValue")
        public Serializable foo(String value) {
            return "from-task: " + value;
        }
    }

    public static class TestTask2 implements Task {

        @SuppressWarnings("UnusedReturnValue")
        public Serializable foo(String value) {
            return "${" + value + "}";
        }
    }
}
