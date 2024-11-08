package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImmutablesTest {

    @Test
    public void test() throws Exception {
        TestBean testBean = ImmutableTestBean.builder()
                .foo("foo")
                .build();

        EvalContextFactory ecf = new EvalContextFactoryImpl();
        ExpressionEvaluator ee = new DefaultExpressionEvaluator(new TaskProviders(), List.of(), List.of());
        Map<String, Object> vars = Collections.singletonMap("testBean", testBean);

        // ---
        String str = ee.eval(ecf.global(new SingleFrameContext(vars)), "Hello ${testBean.foo}", String.class);
        assertEquals("Hello foo", str);
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface TestBean extends Serializable {

        String foo();
    }
}
