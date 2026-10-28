/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

package com.walmartlabs.concord.server.sdk.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContractsTest {
    @Test
    void identityNamespacesAreExactAndOpaque() {
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "Issuer", "Subject");
        assertEquals(key, new ExternalIdentityKey("oidc", "Issuer", "Subject"));
        assertNotEquals(key, new ExternalIdentityKey("oidc", "issuer", "Subject"));
        assertNotEquals(key, new ExternalIdentityKey("oidc", "Issuer", "subject"));
        assertNotEquals(key, new ExternalIdentityKey("OIDC", "Issuer", "Subject"));
        assertEquals("", new ExternalIdentityKey("ldap", "", "user").authority());
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityKey(" ", "", "subject"));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityKey("p", null, "subject"));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityKey("p", "", null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityKey("p", "", ""));
    }

    @Test
    void policyAndLookupRejectInvalidCapabilityIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProvisioningPolicy("", true, "local", Set.of(), List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProvisioningPolicy("sso", true, "local", Set.of(" "), List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProvisioningPolicy("sso", true, "local", Set.of(), List.of("ldap", "ldap"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityLookup("ldap", new DirectoryQuery("username", "alice", null), Set.of(""), true));
    }

    @Test
    void observationsRequirePairedOrderedTimestamps() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "subject");
        UserProfile profile = new UserProfile("alice", null, null, null, Map.of());
        assertThrows(IllegalArgumentException.class, () -> new VerifiedIdentity(key, profile, Set.of(), Map.of(), now, null));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationRequest(UUID.randomUUID(), key, Map.of(), now, now.minusSeconds(1)));
        assertDoesNotThrow(() -> new VerifiedIdentity(key, profile, Set.of(), Map.of(), null, null));
        assertDoesNotThrow(() -> new AuthorizationRequest(UUID.randomUUID(), null, Map.of(), now, now));
    }

    @Test
    void collectionInputsAreSnapshotsAndPreserveJsonNulls() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("optional", null);
        UserProfile profile = new UserProfile("alice", null, null, null, attributes);
        Set<String> groups = new HashSet<>(Set.of("group"));
        AuthorizationData data = new AuthorizationData(groups, Set.of(), Set.of(), null);
        List<String> sources = new ArrayList<>(List.of("ldap"));
        ProvisioningPolicy policy = new ProvisioningPolicy("sso", true, "local", Set.of(), sources, null);
        attributes.put("later", true);
        groups.clear();
        sources.clear();
        assertTrue(profile.attributes().containsKey("optional"));
        assertFalse(profile.attributes().containsKey("later"));
        assertEquals(Set.of("group"), data.groups());
        assertEquals(List.of("ldap"), policy.authorizationSources());
        assertThrows(UnsupportedOperationException.class, () -> profile.attributes().put("x", 1));
    }

    @Test
    void principalRoundTripsWithRestoredProvenance() throws Exception {
        SubjectPrincipal original = new SubjectPrincipal(UUID.randomUUID(), "alice", null,
                new AuthenticationContext("custom", null, null));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(original, in.readObject());
        }
    }

    @Test
    void providerDefaultLoginAgeAndTeamRoleContract() {
        AuthorizationProvider provider = new AuthorizationProvider() {
            public String id() { return "custom"; }
            public boolean isActive() { return true; }
            public boolean acceptsIdentity(ExternalIdentityKey identity) { return identity == null; }
            public Duration defaultMaxAge() { return Duration.ofHours(1); }
            public AuthorizationData resolve(AuthorizationRequest request) { return new AuthorizationData(Set.of(), Set.of(), Set.of(), null); }
        };
        assertEquals(Duration.ofHours(1), provider.defaultLoginMaxAge());
        assertTrue(provider.acceptsIdentity(null));
        assertFalse(provider.acceptsIdentity(new ExternalIdentityKey("custom", "", "subject")));
        assertThrows(IllegalArgumentException.class, () -> new TeamGrant(UUID.randomUUID(), "owner"));
        assertEquals("OWNER", new TeamGrant(UUID.randomUUID(), "OWNER").role());
    }
}
