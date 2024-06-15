package com.walmartlabs.concord.server.process.waits;

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

import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.jooq.enums.ProcessLockScope;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WaitConditionSerializeTest {

    private final ConcordObjectMapper objectMapper = new ConcordObjectMapper(TestObjectMapper.INSTANCE);

    @Test
    public void testProcessLockCondition() throws Exception {
        ProcessLockCondition c = ProcessLockCondition.builder()
                .instanceId(UUID.fromString("e7c43983-95b5-4426-acef-6682b8c1dabe"))
                .orgId(UUID.fromString("64468dec-6279-49b6-b2ee-0540db5953e9"))
                .projectId(UUID.fromString("8e9d1b26-eb23-465b-9862-e64037e4d2e9"))
                .scope(ProcessLockScope.PROJECT)
                .name("test")
                .build();

        String json = objectMapper.toString(c);
        System.out.println(json);

        AbstractWaitCondition cc = objectMapper.fromString(json, AbstractWaitCondition.class);
        assertEquals(c, cc);
    }

    @Test
    public void testProcessCompletionCondition() throws Exception {
        ProcessCompletionCondition c = ProcessCompletionCondition.builder()
                .processes(Collections.singletonList(UUID.fromString("0c443946-0f2c-4685-a9f0-2bc0b735b7ae")))
                .reason("test-reason")
                .exclusive(true)
                .build();

        String json = objectMapper.toString(c);
        System.out.println(json);

        AbstractWaitCondition cc = objectMapper.fromString(json, AbstractWaitCondition.class);
        assertEquals(c, cc);
    }
}
