package com.walmartlabs.concord.server.sdk;

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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.log.ProcessLogEntry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    @Test
    public void testProcessEvent() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        ProcessEvent ev = ProcessEvent.builder()
                .eventSeq(123)
                .processKey(ProcessKey.random())
                .eventType("TEST")
                .eventDate(OffsetDateTime.now())
                .data(Collections.singletonMap("x", "abc"))
                .build();

        String s = om.writeValueAsString(ev);
        ev = om.readValue(s, ProcessEvent.class);

        assertEquals("TEST", ev.eventType());
    }

    @Test
    public void testProcessLog() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        ProcessLogEntry le = ProcessLogEntry.builder()
                .processKey(ProcessKey.random())
                .msg("hello".getBytes())
                .range(Range.builder()
                        .lowerMode(Range.Mode.INCLUSIVE).lower(0)
                        .upperMode(Range.Mode.EXCLUSIVE).upper(5)
                        .build())
                .build();

        String s = om.writeValueAsString(le);
        le = om.readValue(s, ProcessLogEntry.class);

        assertArrayEquals("hello".getBytes(), le.msg());
    }

    @Test
    public void testAuditEvent() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        AuditEvent ae = AuditEvent.builder()
                .entrySeq(0)
                .entryDate(OffsetDateTime.now())
                .userId(UUID.randomUUID())
                .object("TEST")
                .action("DOING A TEST")
                .details(Collections.singletonMap("x", "abc"))
                .build();

        String s = om.writeValueAsString(ae);
        ae = om.readValue(s, AuditEvent.class);

        assertEquals("DOING A TEST", ae.action());
    }
}
