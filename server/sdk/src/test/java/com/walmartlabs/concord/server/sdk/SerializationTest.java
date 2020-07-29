package com.walmartlabs.concord.server.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.log.ProcessLogEntry;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SerializationTest {

    @Test
    public void testProcessEvent() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        ProcessEvent ev = ProcessEvent.builder()
                .eventSeq(123)
                .processKey(new ProcessKeyImpl(UUID.randomUUID(), OffsetDateTime.now()))
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
                .processKey(new ProcessKeyImpl(UUID.randomUUID(), OffsetDateTime.now()))
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
