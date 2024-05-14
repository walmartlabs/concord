package com.walmartlabs.concord.agentoperator.processqueue;

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

import org.junit.jupiter.api.Test;

import static com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueClient.escapeQueryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcessQueueClientTest {

    @Test
    public void testEscapeQueryParam() {
        assertEquals("foo%20bar", escapeQueryParam("foo bar"));
        assertEquals("foo=bar", escapeQueryParam("foo=bar"));
        assertEquals("foo=bar%20baz", escapeQueryParam("foo=bar baz"));
        assertEquals("foo.bar.baz=.*()%23%2F%2F&++$", escapeQueryParam("foo.bar.baz=.*()#//&++$"));
    }
}
