package com.walmartlabs.concord.server.plugins.oidc;

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

import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamEntry;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.Source;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.TeamMapping;
import com.walmartlabs.concord.server.role.RoleDao;
import com.walmartlabs.concord.server.sdk.security.AuthorizationRequest;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.TeamGrant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OidcAuthorizationProviderTest {
    private final PluginConfiguration config = mock(PluginConfiguration.class);
    private final RoleDao roles = mock(RoleDao.class);
    private final TeamDao teams = mock(TeamDao.class);
    private final OidcAuthorizationProvider provider = new OidcAuthorizationProvider(config, roles, teams);
    private final ExternalIdentityKey identity = new ExternalIdentityKey("oidc", "https://issuer", "subject");

    @Test
    void mapsMatchingClaimsToExistingTargetsAndPreservesExpiry() {
        UUID team = UUID.randomUUID();
        UUID missingTeam = UUID.randomUUID();
        when(config.isEnabled()).thenReturn(true);
        when(config.getRoleMapping()).thenReturn(Map.of(
                "admin", List.of(new Source("groups", "admin.*")),
                "missing", List.of(new Source("groups", ".*")),
                "unmatched", List.of(new Source("groups", "other"))));
        when(config.getTeamMapping()).thenReturn(Map.of(
                team, new TeamMapping(List.of(new Source("groups", "dev")), TeamRole.MAINTAINER),
                missingTeam, new TeamMapping(List.of(new Source("groups", "dev")), TeamRole.MEMBER)));
        when(roles.getId("admin")).thenReturn(UUID.randomUUID());
        when(roles.getId("unmatched")).thenReturn(UUID.randomUUID());
        when(teams.get(team)).thenReturn(new TeamEntry(team, UUID.randomUUID(), "team", "team", "team"));
        Instant observed = Instant.now();
        Instant expires = observed.plusSeconds(3600);

        var result = provider.resolve(new AuthorizationRequest(UUID.randomUUID(), identity,
                Map.of("groups", List.of("ADMINS", "dev")), observed, expires));

        assertEquals(Set.of("admin"), result.roles());
        assertEquals(Set.of(new TeamGrant(team, "MAINTAINER")), result.teams());
        assertEquals(Set.of(), result.groups());
        assertEquals(expires, result.validUntil());
        verify(teams).get(team);
        verify(teams).get(missingTeam);
        verifyNoMoreInteractions(teams);
    }

    @Test
    void reevaluationRevokesSourceTeamWithoutRenewingObservation() {
        UUID team = UUID.randomUUID();
        when(config.isEnabled()).thenReturn(true);
        when(config.getRoleMapping()).thenReturn(Map.of());
        when(teams.get(team)).thenReturn(new TeamEntry(team, UUID.randomUUID(), "team", "team", "team"));
        when(config.getTeamMapping()).thenReturn(Map.of(team,
                new TeamMapping(List.of(new Source("groups", "dev")), TeamRole.MEMBER)));
        Instant observed = Instant.now();
        Instant expires = observed.plusSeconds(60);
        var request = new AuthorizationRequest(UUID.randomUUID(), identity, Map.of("groups", List.of("dev")), observed, expires);
        assertEquals(Set.of(new TeamGrant(team, "MEMBER")), provider.resolve(request).teams());
        when(config.getTeamMapping()).thenReturn(Map.of());
        var refreshed = provider.resolve(request);
        assertTrue(refreshed.teams().isEmpty());
        assertEquals(expires, refreshed.validUntil());
    }

    @Test
    void ignoresInvalidMappingsAndReturnsAuthoritativeEmptyGrants() {
        UUID team = UUID.randomUUID();
        when(config.isEnabled()).thenReturn(true);
        when(config.getRoleMapping()).thenReturn(Map.of(
                "invalid", List.of(new Source("groups", "[")),
                "blank", List.of(new Source("", ".*"))));
        when(config.getTeamMapping()).thenReturn(Map.of(team,
                new TeamMapping(List.of(new Source("", ".*")), TeamRole.MEMBER)));
        Instant now = Instant.now();
        var result = provider.resolve(new AuthorizationRequest(UUID.randomUUID(), identity,
                Map.of("groups", List.of("dev")), now, now.plusSeconds(60)));
        assertTrue(result.roles().isEmpty());
        assertTrue(result.teams().isEmpty());
        verifyNoInteractions(roles, teams);
    }

    @Test
    void rejectsUnverifiedExpiredAndWrongIdentityRequests() {
        when(config.isEnabled()).thenReturn(true);
        UUID user = UUID.randomUUID();
        Instant now = Instant.now();
        assertTrue(provider.acceptsIdentity(identity));
        assertFalse(provider.acceptsIdentity(null));
        assertFalse(provider.acceptsIdentity(new ExternalIdentityKey("ldap", "issuer", "subject")));
        assertThrows(AuthorizationUnavailableException.class, () -> provider.resolve(
                new AuthorizationRequest(user, identity, Map.of(), null, null)));
        assertThrows(AuthorizationUnavailableException.class, () -> provider.resolve(
                new AuthorizationRequest(user, identity, Map.of(), now.minusSeconds(120), now.minusSeconds(60))));
        assertThrows(AuthorizationUnavailableException.class, () -> provider.resolve(
                new AuthorizationRequest(user, new ExternalIdentityKey("ldap", "issuer", "subject"), Map.of(), now, now.plusSeconds(60))));
        verifyNoInteractions(roles, teams);
    }
}
