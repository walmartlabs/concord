package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpTaskV2Test {

    @TempDir
    Path tempDir;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    Context ctx;

    HttpTaskV2 task;

    @BeforeEach
    void setUp() {
        when(ctx.processConfiguration().dryRun()).thenReturn(true);
        when(ctx.workingDirectory()).thenReturn(tempDir);
        task = new HttpTaskV2(ctx);
    }

    @Test
    void testExecute() {
        var input = defaultInput();

        var result = execute(input);

        assertTrue(result.ok());
        assertEquals(200, result.values().get("statusCode"));
    }

    private static Map<String, Object> defaultInput() {
        Map<String, Object> defaultInput = new HashMap<>();
        defaultInput.put("url", "https://mock.local");
        defaultInput.put("method", "POST");
        defaultInput.put("request", "json");
        defaultInput.put("body", "{}");

        return defaultInput;
    }

    private TaskResult.SimpleResult execute(Map<String, Object> input) {
        var result = assertDoesNotThrow(() -> task.execute(new MapBackedVariables(input)));
        return assertInstanceOf(TaskResult.SimpleResult.class, result);
    }
}
