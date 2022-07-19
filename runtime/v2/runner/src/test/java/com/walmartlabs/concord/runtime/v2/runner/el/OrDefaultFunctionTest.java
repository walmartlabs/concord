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
import com.walmartlabs.concord.runtime.v2.runner.el.functions.OrDefaultFunction;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.walmartlabs.concord.runtime.v2.runner.el.HasVariableFunctionTest.withVariables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OrDefaultFunctionTest {

    @Test
    public void test() {
        withVariables(Collections.singletonMap("test", "123"), () -> {
            assertEquals("123", OrDefaultFunction.orDefault("test", "11"));
            assertEquals("11", OrDefaultFunction.orDefault("test.nested.deep", "11"));
            assertEquals("11", OrDefaultFunction.orDefault("boom", "11"));
        });

        withVariables(Collections.singletonMap("a", ConfigurationUtils.toNested("b.c", "123")), () -> {
            assertEquals(ConfigurationUtils.toNested("b.c", "123"), OrDefaultFunction.orDefault("a", Collections.emptyMap()));
            assertEquals(Collections.singletonMap("c", "123"), OrDefaultFunction.orDefault("a.b", Collections.emptyMap()));
            assertEquals("123", OrDefaultFunction.orDefault("a.b.c", "x"));
            assertEquals("x", OrDefaultFunction.orDefault("a.b.c.d", "x"));
            assertNull(OrDefaultFunction.orDefault("a.b.c.d", null));
        });
    }
}
