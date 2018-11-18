package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.Test;

import java.util.Date;
import java.util.UUID;

public class ImmutablesTest {

    @Test
    public void test() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());

        ProcessEntry e = ImmutableProcessEntry.
                builder()
                .instanceId(UUID.randomUUID())
                .kind(ProcessKind.DEFAULT)
                .status(ProcessStatus.FINISHED)
                .createdAt(new Date())
                .lastUpdatedAt(new Date())
                .build();

        String s = om.writeValueAsString(e);
        System.out.println(s);

        ProcessEntry e2 = om.readValue(s, ProcessEntry.class);
        System.out.println(e2);
    }
}
