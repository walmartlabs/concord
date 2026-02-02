package com.walmartlabs.concord.it.tasks.schematest;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

/**
 * Test task for schema validation integration tests.
 */
@Named("schemaTest")
public class SchemaTestTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SchemaTestTask.class);

    @Override
    public TaskResult execute(Variables input) {
        String message = input.assertString("message");
        int count = input.getInt("count", 0);

        log.info("SchemaTestTask: message={}, count={}", message, count);

        return TaskResult.success()
                .value("echo", message)
                .value("count", count);
    }
}
