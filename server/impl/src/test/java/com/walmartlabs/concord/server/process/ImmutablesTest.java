package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ImmutablesTest {

    @Test
    public void test() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());

        ProcessEntry e = ImmutableProcessEntry.
                builder()
                .instanceId(UUID.randomUUID())
                .kind(ProcessKind.DEFAULT)
                .status(ProcessStatus.FINISHED)
                .createdAt(OffsetDateTime.now())
                .lastUpdatedAt(OffsetDateTime.now())
                .disabled(false)
                .build();

        String s = om.writeValueAsString(e);
        System.out.println(s);

        ProcessEntry e2 = om.readValue(s, ProcessEntry.class);
        System.out.println(e2);
    }
}
