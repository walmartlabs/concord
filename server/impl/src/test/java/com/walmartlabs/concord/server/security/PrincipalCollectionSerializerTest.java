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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import com.walmartlabs.concord.server.cfg.PrincipalSerializationConfiguration;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationVisibility;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyPrincipalSerializer;
import com.walmartlabs.concord.server.security.github.GithubKey;
import com.walmartlabs.concord.server.security.github.GithubKeyPrincipalSerializer;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipalSerializer;
import com.walmartlabs.concord.server.security.ldap.UsernamePasswordTokenPrincipalSerializer;
import com.walmartlabs.concord.server.sdk.security.PrincipalSerializer;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.SimplePrincipalCollection;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String GENERATED_REALM_NAME = "com.walmartlabs.concord.server.security.ldap.LdapRealm_0";

    private final PrincipalCollectionSerializer serializer;

    public PrincipalCollectionSerializerTest() {
        var objectMapper = new ObjectMapperProvider().get();
        this.serializer = new PrincipalCollectionSerializer(objectMapper, defaultSerializers(objectMapper),
                new PrincipalSerializationConfiguration());
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
        var user = testUser("legacy-user");
        var src = new SimplePrincipalCollection(new UserPrincipal("legacy", user), "legacy");

        var bytes = PrincipalCollectionSerializer.legacySerialize(src);

        var dst = serializer.deserialize(bytes).orElseThrow();
        var userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals("legacy-user", userPrincipal.getUsername());
    }

    @Test
    public void preservesGeneratedShiroRealmNames() {
        var user = testUser("test-user");
        var ldap = new LdapPrincipal("test-user", "example.org", "cn=test-user,dc=example,dc=org", "test-user@example.org",
                "Test User", "test@example.org", Set.of("cn=devs"), Map.of("mail", "test@example.org"));

        var src = new SimplePrincipalCollection();
        src.add(new UserPrincipal("ldap", user), GENERATED_REALM_NAME);
        src.add(ldap, GENERATED_REALM_NAME);

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        assertEquals(Set.of(GENERATED_REALM_NAME), dst.getRealmNames());
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
    public void restoresCustomUserPrincipalSubclasses() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                new UserPrincipalSerializer(objectMapper),
                customUserSerializer(objectMapper)),
                new PrincipalSerializationConfiguration());

        var src = new SimplePrincipalCollection();
        src.add(new CustomUser("custom-realm", testUser("test-user"), "extra-attribute"), "custom-realm");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        var restored = dst.oneByType(CustomUser.class);
        assertNotNull(restored);
        assertEquals(CustomUser.class, restored.getClass());
        assertEquals("extra-attribute", restored.getExtra());
        assertEquals("test-user", restored.getUsername());
        assertEquals("custom-realm", restored.getRealm());
    }

    @Test
    public void failsOnSubclassWithoutExactSerializer() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                new UserPrincipalSerializer(objectMapper)),
                new PrincipalSerializationConfiguration());

        var src = new SimplePrincipalCollection();
        src.add(new CustomUser("custom-realm", testUser("test-user"), "extra-attribute"), "custom-realm");

        var e = assertThrows(IllegalArgumentException.class, () -> serializer.serialize(src));
        assertTrue(e.getMessage().contains(CustomUser.class.getName()));
    }

    @Test
    public void failsOnDuplicateSerializerClasses() {
        var objectMapper = new ObjectMapperProvider().get();

        assertThrows(IllegalArgumentException.class, () -> new PrincipalCollectionSerializer(objectMapper,
                Set.<PrincipalSerializer<?>>of(stringSerializer(objectMapper, "string-a"),
                        stringSerializer(objectMapper, "string-b")),
                new PrincipalSerializationConfiguration()));
    }

    @Test
    public void preservesRealmMembershipAndPrimaryIdentity() {
        var user = testUser("test-user");
        var src = new SimplePrincipalCollection();
        src.add(new UsernamePasswordToken("test-user", "secret".toCharArray(), true), "plugin-realm");
        src.add(new UserPrincipal("plugin-realm", user), "plugin-realm");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        assertEquals(Set.of("plugin-realm"), dst.getRealmNames());

        var token = dst.oneByType(UsernamePasswordToken.class);
        assertNotNull(token);
        assertEquals("test-user", token.getUsername());

        var userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals("plugin-realm", userPrincipal.getRealm());
        assertEquals("test-user", userPrincipal.getUsername());

        var members = dst.fromRealm("plugin-realm");
        assertEquals(2, members.size());
        assertTrue(members.contains(token));
        assertTrue(members.contains(userPrincipal));
    }

    @Test
    public void preservesPrincipalMembershipInMultipleRealms() {
        var keyId = UUID.randomUUID();
        var src = new SimplePrincipalCollection();
        src.add(new ApiKey(keyId, UUID.randomUUID(), "api-key", true), "realm-a");
        src.add(new ApiKey(keyId, UUID.randomUUID(), "api-key", true), "realm-b");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        assertEquals(Set.of("realm-a", "realm-b"), dst.getRealmNames());

        var fromA = (ApiKey) dst.fromRealm("realm-a").iterator().next();
        var fromB = (ApiKey) dst.fromRealm("realm-b").iterator().next();
        assertEquals(keyId, fromA.getKeyId());
        assertEquals(keyId, fromB.getKeyId());
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
                Set.<PrincipalSerializer<?>>of(stringSerializer(objectMapper, "dup"),
                        stringSerializer(objectMapper, "dup")),
                new PrincipalSerializationConfiguration()));
    }

    @Test
    public void failsOnUnsupportedSnapshotHeader() {
        var unsupportedType = "{\"type\":\"other.snapshot.type\",\"version\":1,\"principals\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> serializer.deserialize(unsupportedType));

        var unsupportedVersion = "{\"type\":\"concord.security.principal-collection\",\"version\":2,\"principals\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> serializer.deserialize(unsupportedVersion));
    }

    @Test
    public void failsOnUnsupportedPrincipalTypeInSnapshot() {
        var data = ("{\"type\":\"concord.security.principal-collection\",\"version\":1,"
                + "\"principals\":[{\"realm\":\"custom-realm\",\"type\":\"custom.principal\",\"data\":\"\"}]}")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> serializer.deserialize(data));
    }

    @Test
    public void strictModeRejectsUnregisteredSerializablePrincipals() {
        var objectMapper = new ObjectMapperProvider().get();
        var strict = new PrincipalCollectionSerializer(objectMapper, Set.of(),
                new PrincipalSerializationConfiguration());

        var src = new SimplePrincipalCollection();
        src.add("custom-principal", "custom-realm");

        assertThrows(IllegalArgumentException.class, () -> strict.serialize(src));
    }

    @Test
    public void legacyModeWritesJavaFormatForUnregisteredSerializablePrincipals() throws Exception {
        var objectMapper = new ObjectMapperProvider().get();
        var legacy = new PrincipalCollectionSerializer(objectMapper, Set.of(), legacyWrite());

        var src = new SimplePrincipalCollection();
        src.add("custom-principal", "custom-realm");

        var bytes = legacy.serialize(src);
        assertFalse(new String(bytes, StandardCharsets.UTF_8).startsWith("{"));

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var restored = (SimplePrincipalCollection) ois.readObject();
            assertEquals("custom-principal", restored.oneByType(String.class));
            assertEquals(Set.of("custom-realm"), restored.getRealmNames());
        }
    }

    @Test
    public void jsonModeRoundTripsNonSerializablePrincipalsWithCodecs() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                pluginPrincipalSerializer(objectMapper),
                stringSerializer(objectMapper, "string")),
                new PrincipalSerializationConfiguration());

        var src = new SimplePrincipalCollection();
        src.add(new PluginPrincipal("test-value"), "plugin-realm");
        src.add("custom-principal", "custom-realm");

        var dst = serializer.deserialize(serializer.serialize(src)).orElseThrow();
        assertEquals("test-value", dst.oneByType(PluginPrincipal.class).value());
        assertEquals("custom-principal", dst.oneByType(String.class));
    }

    @Test
    public void missingSerializerFailsNamingTheMissingPrincipal() {
        var objectMapper = new ObjectMapperProvider().get();
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(
                pluginPrincipalSerializer(objectMapper)),
                new PrincipalSerializationConfiguration());

        var src = new SimplePrincipalCollection();
        src.add(new PluginPrincipal("test-value"), "plugin-realm");
        src.add("custom-principal", "custom-realm");

        var e = assertThrows(IllegalArgumentException.class, () -> serializer.serialize(src));
        assertTrue(e.getMessage().contains(String.class.getName()));
        assertNull(e.getCause());
    }

    @Test
    public void legacyModeFailsOnNonSerializablePrincipals() {
        var objectMapper = new ObjectMapperProvider().get();
        var legacy = new PrincipalCollectionSerializer(objectMapper, Set.of(), legacyWrite());

        var src = new SimplePrincipalCollection();
        src.add(new PluginPrincipal("test-value"), "plugin-realm");

        var e = assertThrows(RuntimeException.class, () -> legacy.serialize(src));

        var notSerializable = false;
        for (var c = (Throwable) e; c != null; c = c.getCause()) {
            if (c instanceof NotSerializableException) {
                notSerializable = true;
                break;
            }
        }
        assertTrue(notSerializable, "expected NotSerializableException in the cause chain of " + e);
    }

    @Test
    public void bothWriteModesReadLegacyJavaAndJsonFixtures() {
        var objectMapper = new ObjectMapperProvider().get();
        var strict = new PrincipalCollectionSerializer(objectMapper, defaultSerializers(objectMapper),
                new PrincipalSerializationConfiguration());
        var legacy = new PrincipalCollectionSerializer(objectMapper, defaultSerializers(objectMapper),
                legacyWrite());

        var legacyBytes = PrincipalCollectionSerializer.legacySerialize(
                new SimplePrincipalCollection(new UserPrincipal("legacy", testUser("legacy-user")), "legacy"));
        var jsonBytes = strict.serialize(
                new SimplePrincipalCollection(new UserPrincipal("ldap", testUser("test-user")), "ldap"));

        for (var s : List.of(strict, legacy)) {
            var fromJava = s.deserialize(legacyBytes).orElseThrow();
            assertEquals("legacy-user", fromJava.oneByType(UserPrincipal.class).getUsername());
            assertEquals(Set.of("legacy"), fromJava.getRealmNames());

            var fromJson = s.deserialize(jsonBytes).orElseThrow();
            assertEquals("test-user", fromJson.oneByType(UserPrincipal.class).getUsername());
            assertEquals(Set.of("ldap"), fromJson.getRealmNames());
        }
    }

    @Test
    public void serializeNullRestoresEmptyCollectionInEitherMode() {
        var objectMapper = new ObjectMapperProvider().get();
        var strict = new PrincipalCollectionSerializer(objectMapper, Set.of(),
                new PrincipalSerializationConfiguration());
        var legacy = new PrincipalCollectionSerializer(objectMapper, Set.of(), legacyWrite());

        for (var s : List.of(strict, legacy)) {
            var dst = s.deserialize(s.serialize(null)).orElseThrow();
            assertTrue(dst.asSet().isEmpty());
        }
    }

    @Test
    public void nullReturningCodecFailsTheWholeRead() {
        var objectMapper = new ObjectMapperProvider().get();
        var codec = new AbstractPrincipalSerializer<String>(objectMapper, String.class, "string") {

            @Override
            public byte[] serialize(String principal) {
                return principal.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String deserialize(byte[] data) {
                return null;
            }
        };
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(codec),
                new PrincipalSerializationConfiguration());

        var snapshot = ("{\"type\":\"concord.security.principal-collection\",\"version\":1,"
                + "\"principals\":[{\"realm\":\"r1\",\"type\":\"string\",\"data\":\""
                + Base64.getEncoder().encodeToString("first".getBytes(StandardCharsets.UTF_8))
                + "\"},{\"realm\":\"r2\",\"type\":\"string\",\"data\":\""
                + Base64.getEncoder().encodeToString("second".getBytes(StandardCharsets.UTF_8))
                + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);

        var e = assertThrows(RuntimeException.class, () -> serializer.deserialize(snapshot));
        assertTrue(String.valueOf(e.getCause()).contains("string"));
    }

    @Test
    public void wrongClassCodecResultFailsTheWholeRead() {
        var objectMapper = new ObjectMapperProvider().get();
        var codec = new AbstractPrincipalSerializer<Number>(objectMapper, Number.class, "number") {

            @Override
            public byte[] serialize(Number principal) {
                return principal.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Number deserialize(byte[] data) {
                return Integer.valueOf(new String(data, StandardCharsets.UTF_8));
            }
        };
        var serializer = new PrincipalCollectionSerializer(objectMapper, Set.<PrincipalSerializer<?>>of(codec),
                new PrincipalSerializationConfiguration());

        var snapshot = ("{\"type\":\"concord.security.principal-collection\",\"version\":1,"
                + "\"principals\":[{\"realm\":\"r1\",\"type\":\"number\",\"data\":\""
                + Base64.getEncoder().encodeToString("42".getBytes(StandardCharsets.UTF_8))
                + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);

        var e = assertThrows(RuntimeException.class, () -> serializer.deserialize(snapshot));
        assertTrue(String.valueOf(e.getCause()).contains("number"));
    }

    @Test
    public void decodesHandAuthoredDurableUserPayload() throws Exception {
        var objectMapper = new ObjectMapperProvider().get();
        var node = PrincipalSerializerSupport.toJsonNode(objectMapper,
                DURABLE_USER_PAYLOAD.getBytes(StandardCharsets.UTF_8));
        var principal = PrincipalSerializerSupport.userPrincipal(objectMapper, node);

        assertEquals("ldap", principal.getRealm());
        assertEquals(UUID.fromString("5e9a1a19-6d75-4d16-8b8a-2b5e1c9f0a11"), principal.getId());
        assertEquals("durable-user", principal.getUsername());
        assertEquals(UserType.LDAP, principal.getType());

        var auth = SecurityUtils.toAuthorizationInfo(new SimplePrincipalCollection(principal, "ldap"));
        assertTrue(auth.getRoles().contains("testRole"));
        assertTrue(auth.getStringPermissions().containsAll(Set.of("process:start", "repo:read")));

        var user = principal.getUser();
        var org = user.getOrgs().iterator().next();
        assertEquals("test-org", org.getName());
        assertEquals(OrganizationVisibility.PUBLIC, org.getVisibility());
        assertEquals(Map.of("k", "v"), org.getMeta());
        assertEquals(Map.of("limit", 10), org.getCfg());
        var owner = org.getOwner();
        assertNotNull(owner);
        assertEquals(UUID.fromString("8d3c0e75-2345-4b67-8bcd-ef0123456789"), owner.id());
        assertEquals("org-owner", owner.username());
        assertEquals(UserType.LDAP, owner.userType());

        assertTrue(user.isDisabled());
        assertEquals(OffsetDateTime.parse("2024-05-06T07:08:09.123Z"), user.getDisabledDate());
        assertFalse(user.isPermanentlyDisabled());
    }

    @Test
    public void roundTripsIndependentlyConstructedUserEntries() throws Exception {
        var objectMapper = new ObjectMapperProvider().get();
        var entry = new UserEntry(
                UUID.randomUUID(),
                "rt-user",
                "example.org",
                "RT User",
                Set.of(new OrganizationEntry(
                        UUID.randomUUID(),
                        "rt-org",
                        EntityOwner.builder()
                                .id(UUID.randomUUID())
                                .username("owner")
                                .userDomain("example.org")
                                .displayName("Owner")
                                .userType(UserType.LDAP)
                                .build(),
                        OrganizationVisibility.PRIVATE,
                        Map.of("a", "b"),
                        Map.of("c", "d"))),
                UserType.LDAP,
                "rt@example.org",
                Set.of(new RoleEntry(UUID.randomUUID(), "rtRole", new LinkedHashSet<>(Set.of("p1", "p2")))),
                true,
                OffsetDateTime.parse("2024-05-06T07:08:09.123Z"),
                true);

        var codec = new UserPrincipalSerializer(objectMapper);
        var restored = codec.deserialize(codec.serialize(new UserPrincipal("ldap", entry)));

        assertEquals("ldap", restored.getRealm());
        var u = restored.getUser();
        assertEquals(entry.getId(), u.getId());
        assertEquals("rt-user", u.getName());
        assertEquals("example.org", u.getDomain());
        assertEquals("RT User", u.getDisplayName());
        assertEquals(UserType.LDAP, u.getType());
        assertEquals("rt@example.org", u.getEmail());
        assertTrue(u.isDisabled());
        assertTrue(u.isPermanentlyDisabled());
        assertEquals(OffsetDateTime.parse("2024-05-06T07:08:09.123Z"), u.getDisabledDate());

        var role = u.getRoles().iterator().next();
        assertEquals("rtRole", role.getName());
        assertEquals(Set.of("p1", "p2"), role.getPermissions());

        var org = u.getOrgs().iterator().next();
        assertEquals("rt-org", org.getName());
        assertEquals(OrganizationVisibility.PRIVATE, org.getVisibility());
        assertEquals(Map.of("a", "b"), org.getMeta());
        assertEquals(Map.of("c", "d"), org.getCfg());
        var owner = org.getOwner();
        assertNotNull(owner);
        assertEquals("owner", owner.username());
        assertEquals("example.org", owner.userDomain());
        assertEquals("Owner", owner.displayName());
        assertEquals(UserType.LDAP, owner.userType());
    }

    @Test
    public void decodesUserPayloadWithMissingOptionalFields() throws Exception {
        var objectMapper = new ObjectMapperProvider().get();
        var payload = "{\"realm\":\"internal\",\"user\":{\"id\":\"11111111-2222-3333-4444-555555555555\",\"name\":\"minimal-user\"}}"
                .getBytes(StandardCharsets.UTF_8);

        var principal = PrincipalSerializerSupport.userPrincipal(objectMapper,
                PrincipalSerializerSupport.toJsonNode(objectMapper, payload));

        assertEquals("internal", principal.getRealm());
        assertEquals("minimal-user", principal.getUsername());
        assertNull(principal.getDomain());
        assertNull(principal.getUser().getDisplayName());
        assertNull(principal.getUser().getOrgs());
        assertNull(principal.getUser().getEmail());
        assertNull(principal.getUser().getRoles());
        assertNull(principal.getUser().getDisabledDate());
        assertFalse(principal.getUser().isDisabled());
        assertFalse(principal.getUser().isPermanentlyDisabled());
    }

    private static final String DURABLE_USER_PAYLOAD = """
            {
              "realm": "ldap",
              "user": {
                "id": "5e9a1a19-6d75-4d16-8b8a-2b5e1c9f0a11",
                "name": "durable-user",
                "domain": "example.org",
                "displayName": "Durable User",
                "orgs": [
                  {
                    "id": "7c2b9d64-1234-4a56-9abc-def012345678",
                    "name": "test-org",
                    "owner": {
                      "id": "8d3c0e75-2345-4b67-8bcd-ef0123456789",
                      "username": "org-owner",
                      "userDomain": "example.org",
                      "displayName": "Org Owner",
                      "userType": "LDAP"
                    },
                    "visibility": "PUBLIC",
                    "meta": {"k": "v"},
                    "cfg": {"limit": 10}
                  }
                ],
                "type": "LDAP",
                "email": "durable@example.org",
                "roles": [
                  {
                    "id": "9e4d1f86-3456-4c78-9cde-f01234567890",
                    "name": "testRole",
                    "permissions": ["process:start", "repo:read"]
                  }
                ],
                "disabled": true,
                "disabledDate": "2024-05-06T07:08:09.123Z",
                "permanentlyDisabled": false
              }
            }
            """;

    private static Set<PrincipalSerializer<?>> defaultSerializers(ObjectMapper objectMapper) {
        return Set.of(
                new ApiKeyPrincipalSerializer(objectMapper),
                new GithubKeyPrincipalSerializer(objectMapper),
                new UserPrincipalSerializer(objectMapper),
                new LdapPrincipalSerializer(objectMapper),
                new UsernamePasswordTokenPrincipalSerializer(objectMapper));
    }

    private static PrincipalSerializationConfiguration legacyWrite() {
        return new PrincipalSerializationConfiguration() {

            @Override
            public boolean isLegacyWriteEnabled() {
                return true;
            }
        };
    }

    private static UserEntry testUser(String name) {
        return new UserEntry(UUID.randomUUID(), name, "example.org", "Test User", null, UserType.LDAP,
                "test@example.org", null, false, null, false);
    }

    private static PrincipalSerializer<String> stringSerializer(ObjectMapper objectMapper, String type) {
        return new AbstractPrincipalSerializer<>(objectMapper, String.class, type) {

            @Override
            public byte[] serialize(String principal) {
                return principal.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String deserialize(byte[] data) {
                return new String(data, StandardCharsets.UTF_8);
            }
        };
    }

    private static PrincipalSerializer<PluginPrincipal> pluginPrincipalSerializer(ObjectMapper objectMapper) {
        return new AbstractPrincipalSerializer<>(objectMapper, PluginPrincipal.class, "com.example.plugin.principal") {

            @Override
            public byte[] serialize(PluginPrincipal principal) {
                return principal.value().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public PluginPrincipal deserialize(byte[] data) {
                return new PluginPrincipal(new String(data, StandardCharsets.UTF_8));
            }
        };
    }

    private static PrincipalSerializer<CustomUser> customUserSerializer(ObjectMapper objectMapper) {
        return new AbstractPrincipalSerializer<>(objectMapper, CustomUser.class, "com.example.plugin.custom-user") {

            @Override
            public byte[] serialize(CustomUser principal) {
                var data = (ObjectNode) PrincipalSerializerSupport.toJsonNode(objectMapper, principal);
                data.put("extra", principal.getExtra());
                return PrincipalSerializerSupport.toBytes(objectMapper, data);
            }

            @Override
            public CustomUser deserialize(byte[] data) throws Exception {
                var node = PrincipalSerializerSupport.toJsonNode(objectMapper, data);
                var base = PrincipalSerializerSupport.userPrincipal(objectMapper, node);
                return new CustomUser(base.getRealm(), base.getUser(), PrincipalSerializerSupport.text(node, "extra"));
            }
        };
    }

    private static final class CustomUser extends UserPrincipal {

        private final String extra;

        CustomUser(String realm, UserEntry user, String extra) {
            super(realm, user);
            this.extra = extra;
        }

        String getExtra() {
            return extra;
        }
    }

    private static final class PluginPrincipal {

        private final String value;

        PluginPrincipal(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
