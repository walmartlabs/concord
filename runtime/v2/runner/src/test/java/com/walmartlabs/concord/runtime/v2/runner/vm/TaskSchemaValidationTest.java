package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation;
import com.walmartlabs.concord.runtime.v2.model.TaskCallValidation.ValidationMode;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidationException;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidationResult;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskSchemaValidator;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.svm.Runtime;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskSchemaValidationTest {

    @Test
    void testValidateOutputThrowsInFailMode() {
        Runtime runtime = mock(Runtime.class);
        TaskSchemaValidator validator = mock(TaskSchemaValidator.class);
        when(runtime.getService(TaskSchemaValidator.class)).thenReturn(validator);
        when(validator.validateOutput(eq("testTask"), eq(Task.class), anyMap()))
                .thenReturn(TaskSchemaValidationResult.invalid("testTask.schema.json", List.of("bad output")));

        TaskSchemaValidationException e = assertThrows(TaskSchemaValidationException.class,
                () -> TaskSchemaValidation.validateOutput(runtime,
                        "testTask",
                        Task.class,
                        TaskResult.success(),
                        new TaskCallValidation(ValidationMode.DISABLED, ValidationMode.FAIL)));

        assertEquals("testTask", e.getTaskName());
        assertEquals("out", e.getSection());
        assertEquals("testTask.schema.json", e.getSchemaResource());
        assertEquals(List.of("bad output"), e.getValidationErrors());
    }

    @Test
    void testValidateOutputSkipsSuspendResults() {
        Runtime runtime = mock(Runtime.class);

        TaskSchemaValidation.validateOutput(runtime,
                "testTask",
                Task.class,
                TaskResult.reentrantSuspend("event", Map.of("k", (Serializable) "v")),
                new TaskCallValidation(ValidationMode.DISABLED, ValidationMode.WARN));

        verifyNoInteractions(runtime);
    }

    @Test
    void testValidateOutputSkipsFailedSimpleResults() {
        Runtime runtime = mock(Runtime.class);

        TaskSchemaValidation.validateOutput(runtime,
                "testTask",
                Task.class,
                TaskResult.fail("boom"),
                new TaskCallValidation(ValidationMode.DISABLED, ValidationMode.FAIL));

        verifyNoInteractions(runtime);
    }
}
