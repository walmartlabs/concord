package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyPrincipalSerializer;
import com.walmartlabs.concord.server.security.github.GithubKeyPrincipalSerializer;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipalSerializer;
import com.walmartlabs.concord.server.security.ldap.UsernamePasswordTokenPrincipalSerializer;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrincipalCollectionSerializerTest {

    private final PrincipalCollectionSerializer serializer;

    public PrincipalCollectionSerializerTest() {
        var objectMapper = new ObjectMapperProvider().get();
        this.serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                new ApiKeyPrincipalSerializer(objectMapper),
                new GithubKeyPrincipalSerializer(objectMapper),
                new UserPrincipalSerializer(objectMapper),
                new LdapPrincipalSerializer(objectMapper),
                new UsernamePasswordTokenPrincipalSerializer(objectMapper)));
    }

    @Test
    public void serializesPrincipalsAsJsonAndRestoresKnownTypes() {
        var userId = UUID.randomUUID();
        var keyId = UUID.randomUUID();
        var user = new UserEntry(userId, "test-user", "example.org", "Test User", null, UserType.LDAP, "test@example.org",
                Set.of(new RoleEntry(UUID.randomUUID(), "testRole", Set.of("testPermission"))), false, null, false);
        var ldap = new LdapPrincipal("test-user", "example.org", "cn=test-user,dc=example,dc=org", "test-user@example.org",
                "Test User", "test@example.org", Set.of("cn=devs"), Map.of("mail", "test@example.org"));
        var apiKey = new ApiKey(keyId, userId, "api-key", true);
        var usernamePassword = new UsernamePasswordToken("test-user", "secret".toCharArray(), true);

        var src = new SimplePrincipalCollection();
        src.add(new UserPrincipal("ldap", user), "ldap");
        src.add(ldap, "ldap");
        src.add(apiKey, "apikey");
        src.add(usernamePassword, "ldap");

        var bytes = serializer.serialize(src);
        assertEquals('{', new String(bytes, StandardCharsets.UTF_8).charAt(0));

        var dst = serializer.deserialize(bytes).orElseThrow();
        var userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals(userId, userPrincipal.getId());
        assertEquals("testRole", userPrincipal.getUser().getRoles().iterator().next().getName());

        var ldapPrincipal = dst.oneByType(LdapPrincipal.class);
        assertNotNull(ldapPrincipal);
        assertEquals("test-user", ldapPrincipal.getUsername());
        assertEquals(Set.of("cn=devs"), ldapPrincipal.getGroups());

        var restoredApiKey = dst.oneByType(ApiKey.class);
        assertNotNull(restoredApiKey);
        assertEquals(keyId, restoredApiKey.getKeyId());
        assertTrue(restoredApiKey.isRememberMe());

        var restoredUsernamePassword = dst.oneByType(UsernamePasswordToken.class);
        assertNotNull(restoredUsernamePassword);
        assertEquals("test-user", restoredUsernamePassword.getUsername());
        assertArrayEquals("secret".toCharArray(), restoredUsernamePassword.getPassword());
        assertTrue(restoredUsernamePassword.isRememberMe());
    }

    @Test
    public void deserializesLegacyJavaPrincipalCollections() {
        var user = new UserEntry(UUID.randomUUID(), "legacy-user", null, null, null, UserType.LOCAL, null, null, false, null, false);
        var src = new SimplePrincipalCollection(new UserPrincipal("legacy", user), "legacy");

        var bytes = PrincipalCollectionSerializer.legacySerialize(src);

        var dst = serializer.deserialize(bytes).orElseThrow();
        var userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals("legacy-user", userPrincipal.getUsername());
    }

    @Test
    public void normalizesGeneratedShiroRealmNames() {
        var user = new UserEntry(UUID.randomUUID(), "test-user", "example.org", "Test User", null, UserType.LDAP, "test@example.org",
                null, false, null, false);
        var ldap = new LdapPrincipal("test-user", "example.org", "cn=test-user,dc=example,dc=org", "test-user@example.org",
                "Test User", "test@example.org", Set.of("cn=devs"), Map.of("mail", "test@example.org"));

        var src = new SimplePrincipalCollection();
        src.add(new UserPrincipal("ldap", user), "com.walmartlabs.concord.server.security.ldap.LdapRealm_0");
        src.add(ldap, "com.walmartlabs.concord.server.security.ldap.LdapRealm_0");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        assertTrue(dst.getRealmNames().contains("ldap"));
        assertNotNull(dst.oneByType(LdapPrincipal.class));
    }
}
