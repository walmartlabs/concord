package com.walmartlabs.concord.server.plugins.oneops;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.events.ExternalEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OneOpsEventMapperTest {

    @Test
    void mapsDeployment() {
        Map<String, Object> raw = deployment("alice");

        ExternalEvent event = OneOpsEventMapper.map("123", raw, 2);

        assertEquals("oneops", event.source());
        assertEquals("123", event.id());
        assertEquals(2, event.triggerVersion());
        assertEquals("myorg", event.conditions().get("org"));
        assertEquals("myasm", event.conditions().get("asm"));
        assertEquals("prod", event.conditions().get("env"));
        assertEquals("myplatform", event.conditions().get("platform"));
        assertEquals("complete", event.conditions().get("deploymentState"));
        assertEquals(Set.of("10.0.0.1", "10.0.0.2"), event.conditions().get("ips"));
        assertEquals("alice", event.conditions().get("author"));
        assertEquals("alice", event.attributes().get("author"));
        assertEquals("alice", event.initiatorUsername());
        assertEquals(2, event.conditions().get("version"));
        assertEquals(2, event.attributes().get("version"));
        assertSame(raw, event.attributes().get("payload"));
    }

    @Test
    void preservesMissingAuthor() {
        Map<String, Object> raw = deployment(null);

        ExternalEvent event = OneOpsEventMapper.map("123", raw, 2);

        assertTrue(event.conditions().containsKey("author"));
        assertTrue(event.attributes().containsKey("author"));
        assertNull(event.conditions().get("author"));
        assertNull(event.attributes().get("author"));
        assertNull(event.initiatorUsername());
    }

    private static Map<String, Object> deployment(String author) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deploymentState", "complete");
        if (author != null) {
            payload.put("createdBy", author);
        }

        return Map.of(
                "payload", payload,
                "cis", List.of(
                        Map.of("nsPath", "/myorg/myasm/prod/bom/myplatform/1",
                                "ciAttributes", Map.of("public_ip", "10.0.0.1")),
                        Map.of("ciAttributes", Map.of("public_ip", "10.0.0.1")),
                        Map.of("ciAttributes", Map.of("public_ip", "10.0.0.2")),
                        Map.of("ciAttributes", Map.of()),
                        Map.of()));
    }
}
