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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyPrincipalSerializer;
import com.walmartlabs.concord.server.security.github.GithubKey;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void restoresGithubKeys() {
        var projectId = UUID.randomUUID();
        var src = new SimplePrincipalCollection();
        src.add(new GithubKey("test-key", projectId, "repo-token"), "github");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        var key = dst.oneByType(GithubKey.class);
        assertNotNull(key);
        assertEquals("test-key", key.getKey());
        assertEquals(projectId, key.getProjectId());
        assertEquals("repo-token", key.getRepoToken());
    }

    @Test
    public void restoresGithubKeysWithoutOptionalFields() {
        var src = new SimplePrincipalCollection();
        src.add(new GithubKey("test-key", null, null), "github");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        var key = dst.oneByType(GithubKey.class);
        assertNotNull(key);
        assertEquals("test-key", key.getKey());
        assertNull(key.getProjectId());
        assertNull(key.getRepoToken());
    }

    @Test
    public void serializesUnsupportedPrincipalsUsingLegacyFormat() {
        var src = new SimplePrincipalCollection();
        src.add("custom-principal", "custom-realm");

        var bytes = serializer.serialize(src);
        assertFalse(new String(bytes, StandardCharsets.UTF_8).startsWith("{"));

        var dst = serializer.deserialize(bytes).orElseThrow();
        assertEquals("custom-principal", dst.oneByType(String.class));
    }

    @Test
    public void roundTripsEmptyCollections() {
        var dst = serializer.deserialize(serializer.serialize(new SimplePrincipalCollection())).orElseThrow();
        assertTrue(dst.asSet().isEmpty());
    }

    @Test
    public void emptyInputDeserializesToNothing() {
        assertTrue(serializer.deserialize((byte[]) null).isEmpty());
        assertTrue(serializer.deserialize(new byte[0]).isEmpty());
    }

    @Test
    public void failsOnDuplicateSerializerTypes() {
        var objectMapper = new ObjectMapperProvider().get();

        assertThrows(IllegalArgumentException.class, () -> new PrincipalCollectionSerializer(objectMapper,
                Set.<PrincipalSerializer<?>>of(stringSerializer(objectMapper, "dup", null),
                        stringSerializer(objectMapper, "dup", null))));
    }

    @Test
    public void failsOnAmbiguousSerializers() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                stringSerializer(objectMapper, "string", null),
                objectSerializer(objectMapper)));

        var src = new SimplePrincipalCollection();
        src.add("custom-principal", "custom-realm");

        var e = assertThrows(IllegalArgumentException.class, () -> serializer.serialize(src));
        assertTrue(e.getMessage().contains("Ambiguous principal serializers"));
    }

    @Test
    public void failsOnAmbiguousRealmNames() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                stringSerializer(objectMapper, "string", "realm-a"),
                uuidSerializer(objectMapper, "uuid", "realm-b")));

        var src = new SimplePrincipalCollection();
        src.add("custom-principal", "generated");
        src.add(UUID.randomUUID(), "generated");

        var e = assertThrows(IllegalArgumentException.class, () -> serializer.serialize(src));
        assertTrue(e.getMessage().contains("Ambiguous principal realms"));
    }

    @Test
    public void failsOnUnsupportedSnapshotHeader() {
        var unsupportedType = "{\"type\":\"other.snapshot.type\",\"version\":1,\"principals\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        var e = assertThrows(RuntimeException.class, () -> serializer.deserialize(unsupportedType));
        assertTrue(e.getCause().getMessage().contains("Unsupported principal snapshot type"));

        var unsupportedVersion = "{\"type\":\"concord.security.principal-collection\",\"version\":2,\"principals\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        var e2 = assertThrows(RuntimeException.class, () -> serializer.deserialize(unsupportedVersion));
        assertTrue(e2.getCause().getMessage().contains("Unsupported principal snapshot version"));
    }

    @Test
    public void failsOnUnsupportedPrincipalTypeInSnapshot() {
        var data = ("{\"type\":\"concord.security.principal-collection\",\"version\":1,"
                + "\"principals\":[{\"realm\":\"custom-realm\",\"type\":\"custom.principal\",\"data\":\"\"}]}")
                .getBytes(StandardCharsets.UTF_8);

        var e = assertThrows(RuntimeException.class, () -> serializer.deserialize(data));
        assertTrue(e.getCause().getMessage().contains("Unsupported principal snapshot type: custom.principal"));
    }

    private static PrincipalSerializer<String> stringSerializer(ObjectMapper objectMapper, String type, String realmName) {
        return new AbstractPrincipalSerializer<>(objectMapper, String.class, type, realmName) {

            @Override
            public byte[] serialize(String principal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String deserialize(byte[] data) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PrincipalSerializer<UUID> uuidSerializer(ObjectMapper objectMapper, String type, String realmName) {
        return new AbstractPrincipalSerializer<>(objectMapper, UUID.class, type, realmName) {

            @Override
            public byte[] serialize(UUID principal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UUID deserialize(byte[] data) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PrincipalSerializer<Object> objectSerializer(ObjectMapper objectMapper) {
        return new AbstractPrincipalSerializer<>(objectMapper, Object.class, "object", null) {

            @Override
            public byte[] serialize(Object principal) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object deserialize(byte[] data) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
