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
import com.walmartlabs.concord.server.cfg.PrincipalSerializationConfiguration;
import com.walmartlabs.concord.server.security.PrincipalCollectionSerializer;
import com.walmartlabs.concord.server.sdk.security.PrincipalSerializer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.UserPrincipalSerializer;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipalSerializer;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import java.util.Map;
import java.util.UUID;
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
                Set.<PrincipalSerializer<?>>of(new SsoTokenPrincipalSerializer(objectMapper)),
                new PrincipalSerializationConfiguration());

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
    @Test
    public void roundTripsMixedPrincipalSets() {
        var objectMapper = new ObjectMapper();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                new SsoTokenPrincipalSerializer(objectMapper),
                new UserPrincipalSerializer(objectMapper),
                new LdapPrincipalSerializer(objectMapper)),
                new PrincipalSerializationConfiguration());

        var token = new SsoToken("test-user", "example.org", "Test User", "test-user@example.org",
                "test-user@example.org", "cn=test-user,dc=example,dc=org", Set.of("devs", "admins"));
        var user = new UserEntry(UUID.randomUUID(), "sso-user", "example.org", "SSO User", null, UserType.SSO,
                "test-user@example.org", null, false, null, false);
        var ldap = new LdapPrincipal("test-user", "example.org", "cn=test-user,dc=example,dc=org",
                "test-user@example.org", "Test User", "test-user@example.org", Set.of("devs"),
                Map.of("mail", "test-user@example.org"));

        var src = new SimplePrincipalCollection();
        src.add(token, SsoRealm.REALM_NAME);
        src.add(new UserPrincipal(SsoRealm.REALM_NAME, user), SsoRealm.REALM_NAME);
        src.add(ldap, SsoRealm.REALM_NAME);

        var bytes = serializer.serialize(src);
        assertEquals('{', new String(bytes, StandardCharsets.UTF_8).charAt(0));

        var dst = serializer.deserialize(bytes).orElseThrow();
        assertEquals(Set.of(SsoRealm.REALM_NAME), dst.getRealmNames());
        assertEquals(3, dst.fromRealm(SsoRealm.REALM_NAME).size());

        var restoredToken = dst.oneByType(SsoToken.class);
        assertNotNull(restoredToken);
        assertEquals("test-user", restoredToken.getUsername());
        assertEquals(Set.of("devs", "admins"), restoredToken.getGroups());

        var restoredUser = dst.oneByType(UserPrincipal.class);
        assertNotNull(restoredUser);
        assertEquals("sso-user", restoredUser.getUsername());
        assertEquals(UserType.SSO, restoredUser.getType());
        assertEquals(SsoRealm.REALM_NAME, restoredUser.getRealm());

        var restoredLdap = dst.oneByType(LdapPrincipal.class);
        assertNotNull(restoredLdap);
        assertEquals("test-user", restoredLdap.getUsername());
        assertEquals(Set.of("devs"), restoredLdap.getGroups());
    }
}
