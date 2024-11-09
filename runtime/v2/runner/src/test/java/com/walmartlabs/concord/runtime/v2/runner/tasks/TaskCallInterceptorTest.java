package com.walmartlabs.concord.runtime.v2.runner.tasks;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TaskCallInterceptorTest {

    @Test
    public void methodAnnotationsTest() {
        Base base = new Base();
        String method = "varargs";
        List<Object> params = Arrays.asList("one", "two");

        Method m = Method.of(base.getClass(), method, params);

        assertEquals(0, m.annotations().size());
    }

    public static class Base implements Task {

        public void varargs(Object ... args) {

        }
    }
}
