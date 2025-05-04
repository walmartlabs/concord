package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.MockContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpTaskTest {

    @Test
    void testExecute() {
        Map<String, Object> input = new HashMap<>();
        input.put("url", "https://mock.local");

        var ctx = new MockContext(input);
        assertDoesNotThrow(() -> new MockHttpTask().execute(ctx));

        assertNotNull(ctx.getVariable("response"));
    }

    @Test
    void testExecuteCustomOutVar() {
        Map<String, Object> input = new HashMap<>();
        input.put("url", "https://mock.local");
        input.put("out", "myOut");

        var ctx = new MockContext(input);
        assertDoesNotThrow(() -> new MockHttpTask().execute(ctx));

        assertNotNull(ctx.getVariable("myOut"));
    }

    @Test
    void testAsString() {
        var out = assertDoesNotThrow(() -> new MockHttpTask().asString("https://mock.local"));
        assertEquals("ok", out);
    }

    private static class MockHttpTask extends HttpTask {

        @Override
        Map<String, Object> executeRequest(Configuration config) {
            return Map.of("success", true, "content", "ok");
        }
    }

}
