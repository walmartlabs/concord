package com.walmartlabs.concord.it.console;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
public class TryingIT extends Base {

    @RegisterExtension
    public static TestRule rule = new TestRule();

    @Test
    public void testTimeout() throws Exception {
        System.out.println("first");
        Thread.sleep(10_000);
    }

    @Test
    public void testTimeout2() throws Exception {
        System.out.println("second");
        Thread.sleep(10_000);
    }

    public static class TestRule implements BeforeEachCallback, TestExecutionExceptionHandler, AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) {
            System.out.println("afterEach");
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            System.out.println("beforeEach");
        }

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            String fileName = context.getTestClass().get().getName() + context.getTestMethod().get().getName() + ".png";
            System.out.println("handleTestExecutionException:" + fileName);
        }
    }
}
