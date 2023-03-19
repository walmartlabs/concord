package com.walmartlabs.concord.server.events;

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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionUtilsTest {

    @Test
    public void simpleTest() {
        Map<String, Object> event = new HashMap<>();
        event.put("k", "v");
        event.put("commitMessage", "some text ${oops} moo");

        Map<String, Object> escaped = ExpressionUtils.escapeMap(event);
        assertEquals(event.size(), escaped.size());
        assertEquals(event.get("k"), escaped.get("k"));
        assertEquals("some text \\${oops} moo", escaped.get("commitMessage"));
    }
}
