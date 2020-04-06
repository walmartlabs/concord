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
import org.junit.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

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
}
