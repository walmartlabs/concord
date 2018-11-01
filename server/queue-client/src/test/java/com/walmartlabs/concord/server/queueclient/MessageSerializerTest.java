package com.walmartlabs.concord.server.queueclient;

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

import com.walmartlabs.concord.server.queueclient.message.*;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MessageSerializerTest {

    @Test
    public void testCommandRequest() {
        CommandRequest r = new CommandRequest(UUID.randomUUID());
        r.setCorrelationId(123);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        CommandRequest rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.COMMAND_REQUEST);
        assertEquals(r.getAgentId(), rDeserialized.getAgentId());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());
    }

    @Test
    public void testCommandResponse() {
        CommandResponse r = new CommandResponse(123, CommandResponse.CommandType.CANCEL_JOB, null);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        CommandResponse rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.COMMAND_RESPONSE);
        assertEquals(r.getType(), rDeserialized.getType());
        assertEquals(r.getPayload(), rDeserialized.getPayload());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());
    }

    @Test
    public void testProcessRequest() {
        ProcessRequest r = new ProcessRequest(Collections.singletonMap("k", "v"));
        r.setCorrelationId(123);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        ProcessRequest rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.PROCESS_REQUEST);
        assertEquals(r.getCapabilities(), rDeserialized.getCapabilities());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());
    }

    @Test
    public void testProcessResponse() {
        ProcessResponse r = new ProcessResponse(123, UUID.randomUUID());

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        ProcessResponse rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.PROCESS_RESPONSE);
        assertEquals(r.getProcessId(), rDeserialized.getProcessId());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());
    }
}
