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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class InterpolatorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testMaps() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());

        GlobalVariables vars = new GlobalVariablesImpl(Collections.singletonMap("name", "Concord"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("msg", "Hello, ${name}");
        input.put("text", "Sending \"${msg}\"");

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("msg", "Hello, Concord");
        expected.put("text", "Sending \"Hello, Concord\"");

        Map<String, Object> output = Interpolator.interpolate(ee, new DummyContext(vars), input, Map.class);
        assertThat(output, is(expected));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCycles() {
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders());
        GlobalVariables vars = new GlobalVariablesImpl();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", "123");
        input.put("b", Collections.singletonMap("x", "${b}"));

        try {
            Interpolator.interpolate(ee, new DummyContext(vars), input, Map.class);
            fail("should fail");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("null base Object with identifier 'b'"));
        }

        vars = new GlobalVariablesImpl(Collections.singletonMap("b", "234"));

        input = new LinkedHashMap<>();
        input.put("a", "123");
        input.put("b", Collections.singletonMap("x", "${b}"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", "123");
        expected.put("b", Collections.singletonMap("x", "234"));

        Map<String, Object> output = Interpolator.interpolate(ee, new DummyContext(vars), input, Map.class);
        assertThat(output, is(expected));
    }
}
