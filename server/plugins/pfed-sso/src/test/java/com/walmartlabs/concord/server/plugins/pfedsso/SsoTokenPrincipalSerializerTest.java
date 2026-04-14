package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.security.PrincipalCollectionSerializer;
import com.walmartlabs.concord.server.security.PrincipalSerializer;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SsoTokenPrincipalSerializerTest {

    @Test
    public void roundTripsSsoTokens() {
        var objectMapper = new ObjectMapper();
        var serializer = new PrincipalCollectionSerializer(objectMapper,
                Set.<PrincipalSerializer<?>>of(new SsoTokenPrincipalSerializer(objectMapper)));

        var token = new SsoToken("test-user", "example.org", "Test User", "test-user@example.org",
                "test-user@example.org", "cn=test-user,dc=example,dc=org", Set.of("devs", "admins"));

        var src = new SimplePrincipalCollection();
        src.add(token, SsoRealm.REALM_NAME);

        var bytes = serializer.serialize(src);
        assertEquals('{', new String(bytes, StandardCharsets.UTF_8).charAt(0));

        var dst = serializer.deserialize(bytes).orElseThrow();
        var restored = dst.oneByType(SsoToken.class);
        assertNotNull(restored);
        assertEquals("test-user", restored.getUsername());
        assertEquals("example.org", restored.getDomain());
        assertEquals("Test User", restored.getDisplayName());
        assertEquals("test-user@example.org", restored.getMail());
        assertEquals("test-user@example.org", restored.getUserPrincipalName());
        assertEquals("cn=test-user,dc=example,dc=org", restored.getNameInNamespace());
        assertEquals(Set.of("devs", "admins"), restored.getGroups());
    }
}
