package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.assertLog;

public class MainTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    public void testTaskMock() throws Exception {
        runtime.deploy("simple");

        byte[] log = runtime.run();
        assertLog(log, ".*The actual task is not being executed; this is a mock.*");
        assertLog(log, ".*result.ok: true.*");
    }

    @Test
    public void testMethodMock() throws Exception {
        runtime.deploy("method-mock");

        byte[] log = runtime.run();
        assertLog(log, ".*The actual task is not being executed; this is a mock.*");
        assertLog(log, ".*result.ok: true.*");
    }
}
