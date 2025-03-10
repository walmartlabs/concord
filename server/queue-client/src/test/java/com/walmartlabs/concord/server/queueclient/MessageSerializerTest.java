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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.Import.SecretDefinition;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.server.queueclient.message.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    public void testUnknownProperties() {
        String str = "{\"sessionToken\":\"123123\", \"correlationId\":123, \"processId\":\"b26a60c6-b54e-4f4d-bf0a-abafb908bf76\", \"messageType\":\"PROCESS_RESPONSE\"}";
        ProcessResponse rDeserialized = MessageSerializer.deserialize(str);
        assertEquals(123, rDeserialized.getCorrelationId());
    }

    @Test
    public void testProcessResponse() {
        SecretDefinition secret = SecretDefinition.builder()
                .org("secret-org")
                .name("secret-name")
                .password("secret-password")
                .build();

        Import item = Import.GitDefinition.builder()
                .url("http://url")
                .version("master")
                .dest("concord")
                .path("path1")
                .secret(secret)
                .build();

        Imports imports = Imports.of(Collections.singletonList(item));

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneId.of("UTC")).minusMinutes(10).truncatedTo(ChronoUnit.MILLIS); // mimic the DB time
        ProcessResponse r = new ProcessResponse(123, "sesion-token", UUID.randomUUID(), createdAt, "org-name", "repo-url", "repo-path", "commit-id", "repo-branch", "secret-name", imports);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        ProcessResponse rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(MessageType.PROCESS_RESPONSE, r.getMessageType());
        assertEquals(r.getSessionToken(), rDeserialized.getSessionToken());
        assertEquals(r.getProcessId(), rDeserialized.getProcessId());
        assertEquals(createdAt, rDeserialized.getProcessCreatedAt());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());
        assertEquals("repo-branch", rDeserialized.getRepoBranch());
    }
}
