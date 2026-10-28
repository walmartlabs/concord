package com.walmartlabs.concord.server.org.team;

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
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamUsersResourceTest {
    @Test
    void uuidRemovalDelegatesWithoutNameLookup() {
        TeamManager manager = mock(TeamManager.class);
        TeamUsersV2Resource resource = new TeamUsersV2Resource(manager);
        UUID id = UUID.randomUUID();
        resource.removeUsers("org", "team", List.of(id));
        verify(manager).removeUsersById("org", "team", List.of(id));
        assertThrows(ValidationErrorsException.class, () -> resource.removeUsers("org", "team", List.of()));
        assertThrows(ValidationErrorsException.class, () -> resource.removeUsers("org", "team", Arrays.asList(id, null)));
        verifyNoMoreInteractions(manager);
    }

    @Test
    void identityAndSourceMetadataRoundTripAndLegacyFieldsCanBeOmitted() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID id = UUID.randomUUID();
        TeamUserEntry entry = new TeamUserEntry(id, "alice", null, "Alice", UserType.LOCAL,
                TeamRole.MEMBER, TeamMemberType.LDAP_GROUP, "staff",
                new ExternalIdentityKey("oidc", "issuer", "subject"), TeamUserEntry.MembershipKind.SOURCE,
                List.of(new TeamUserEntry.GrantSource("oidc", null, TeamRole.MAINTAINER)));
        TeamUserEntry decoded = mapper.readValue(mapper.writeValueAsString(entry), TeamUserEntry.class);
        assertEquals(entry.getIdentity(), decoded.getIdentity());
        assertEquals(entry.getGrantSources(), decoded.getGrantSources());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(entry);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            TeamUserEntry legacy = (TeamUserEntry) in.readObject();
            assertEquals(id, legacy.getUserId());
            assertNull(legacy.getIdentity());
            assertNull(legacy.getMembershipKind());
            assertNull(legacy.getGrantSources());
        }
        var json = mapper.readTree(mapper.writeValueAsString(decoded.withoutLegacyFields()));
        assertFalse(json.has("userType"));
        assertFalse(json.has("memberType"));
        assertFalse(json.has("ldapGroupSource"));
        assertEquals("SOURCE", json.get("membershipKind").asText());
        assertTrue(json.get("grantSources").get(0).get("groupId").isNull());
    }
}
