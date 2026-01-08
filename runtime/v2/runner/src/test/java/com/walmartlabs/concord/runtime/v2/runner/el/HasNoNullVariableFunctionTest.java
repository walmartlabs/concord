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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.runner.el.functions.HasNonNullVariableFunction;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HasNoNullVariableFunctionTest {

    @Test
    public void test() {
        withVariables(Collections.singletonMap("test", "123"), () -> {
            assertTrue(HasNonNullVariableFunction.hasNonNullVariable("test"));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable("test.nested.deep"));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable("boom"));
        });

        withVariables(Collections.singletonMap("testNull", null), () -> {
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable("testNull"));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable("testNull.k2"));
        });

        withVariables(Collections.singletonMap("a", ConfigurationUtils.toNested("b.c", "123")), () -> {
            assertTrue(HasNonNullVariableFunction.hasNonNullVariable("a"));
            assertTrue(HasNonNullVariableFunction.hasNonNullVariable("a.b"));
            assertTrue(HasNonNullVariableFunction.hasNonNullVariable("a.b.c"));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable("a.b.c.d"));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable(""));
            assertFalse(HasNonNullVariableFunction.hasNonNullVariable(null));
        });
    }

    public static void withVariables(Map<String, Object> variables, Runnable runnable) throws RuntimeException {
        ThreadLocalEvalContext.withEvalContext(new EvalContext() {
            @Override
            public Context context() {
                return null;
            }

            @Override
            public Variables variables() {
                return new MapBackedVariables(variables);
            }
        }, () -> {
            runnable.run();
            return null;
        });
    }
}
