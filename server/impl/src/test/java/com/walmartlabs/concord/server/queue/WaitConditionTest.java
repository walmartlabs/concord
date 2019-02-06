package com.walmartlabs.concord.server.queue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.process.queue.WaitCondition;
import com.walmartlabs.concord.server.process.queue.WaitType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WaitConditionTest {

    @Test
    public void test() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        WaitCondition c1 = WaitCondition.builder()
                .type(WaitType.PROCESS_COMPLETION)
                .putPayload("x", 123)
                .build();

        String s = objectMapper.writeValueAsString(c1);

        WaitCondition c2 = objectMapper.readValue(s, WaitCondition.class);
        assertEquals(c2.payload().get("x"), c1.payload().get("x"));
    }
}
