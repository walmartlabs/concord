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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.project.model.Import;
import com.walmartlabs.concord.server.queueclient.message.*;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

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
        Import.SecretDefinition secret = Import.SecretDefinition.builder()
                .org("secret-org")
                .name("secret-name")
                .password("secret-password")
                .build();
        ImportEntry item = ImportEntry.GitEntry.builder()
                .url("http://url")
                .version("master")
                .dest("concord")
                .path("path1")
                .secret(secret)
                .build();
        Imports imports = Imports.builder().addItems(item).build();
        ProcessResponse r = new ProcessResponse(123, UUID.randomUUID(), "org-name", "repo-url", "repo-path", "commit-id", "secret-name", imports);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        ProcessResponse rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.PROCESS_RESPONSE);
        assertEquals(r.getProcessId(), rDeserialized.getProcessId());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());

        // imports
        assertEquals(imports.items().size(), rDeserialized.getImports().items().size());
        assertEquals(imports.items().get(0), rDeserialized.getImports().items().get(0));
    }

    @Test
    public void testProcessResponseWithoutImports() {
        ProcessResponse r = new ProcessResponse(123, UUID.randomUUID(), "org-name", "repo-url", "repo-path", "commit-id", "secret-name", null);

        // ---
        String rSerialized = MessageSerializer.serialize(r);
        assertNotNull(rSerialized);

        ProcessResponse rDeserialized = MessageSerializer.deserialize(rSerialized);
        assertEquals(r.getMessageType(), MessageType.PROCESS_RESPONSE);
        assertEquals(r.getProcessId(), rDeserialized.getProcessId());
        assertEquals(r.getCorrelationId(), rDeserialized.getCorrelationId());

        // imports
        assertNull(rDeserialized.getImports());
    }

    @Test
    public void testImports() throws Exception {
        Import.SecretDefinition secret = Import.SecretDefinition.builder()
                .org("secret-org")
                .name("secret-name")
                .password("secret-password")
                .build();

        ImportEntry item = ImportEntry.GitEntry.builder()
                .url("http://url")
                .version("master")
                .dest("concord")
                .path("path1")
                .secret(secret)
                .build();

        ImmutableImports imports = Imports.builder()
                .addItems(item)
                .build();

        // ---
        ObjectMapper om = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());

        String rSerialized = om.writeValueAsString(imports);
        assertNotNull(rSerialized);

        Imports rDeserialized = om.readValue(rSerialized, Imports.class);
        assertEquals(imports, rDeserialized);
    }
}
