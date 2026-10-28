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

import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.user.AccountSelector;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TeamUserSelectorTest {
    private final UUID orgId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TeamDao teams = mock(TeamDao.class);
    private final OrganizationDao orgs = mock(OrganizationDao.class);
    private final OrganizationManager organizations = mock(OrganizationManager.class);
    private final UserManager users = mock(UserManager.class);
    private final IdentityDao identities = mock(IdentityDao.class);
    private final DSLContext tx = mock(DSLContext.class);
    private final TeamManager manager = spy(new TeamManager(teams, orgs, organizations, users, mock(AuditLog.class, RETURNS_DEEP_STUBS)));

    private void setup(boolean legacy) throws Exception {
        manager.accountSelector = new AccountSelector(identities, ConfigFactory.parseString("security.legacy.enabled=" + legacy));
        OrganizationEntry org = mock(OrganizationEntry.class);
        when(org.getId()).thenReturn(orgId);
        when(organizations.assertAccess("org", true)).thenReturn(org);
        doReturn(new TeamEntry(teamId, orgId, "org", "team", null)).when(manager)
                .assertAccess(orgId, "team", TeamRole.MAINTAINER, true);
        when(orgs.hasOwner(tx, orgId)).thenReturn(true);
        doAnswer(invocation -> { invocation.<AbstractDao.Tx>getArgument(0).run(tx); return null; }).when(teams).tx(any());
    }

    private TeamUserEntry entry(UUID id, String name, UserType type, ExternalIdentityKey identity) {
        return new TeamUserEntry(id, name, null, null, type, TeamRole.MEMBER, null, null, identity, null, null);
    }

    @Test
    void identityResolvesAndLegacyUuidPrecedenceRemains() throws Exception {
        setup(true);
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "subject");
        when(identities.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), userId, key, true)));
        manager.addUsers("org", "team", false, List.of(entry(null, null, null, key)));
        manager.addUsers("org", "team", false, List.of(entry(userId, "old-name", UserType.LDAP, null)));
        verify(teams, times(2)).upsertUser(tx, teamId, userId, TeamRole.MEMBER);
        verifyNoInteractions(users);
        assertThrows(ValidationErrorsException.class, () -> manager.addUsers("org", "team", false,
                List.of(entry(userId, null, null, key))));
        assertThrows(ValidationErrorsException.class, () -> manager.addUsers("org", "team", false,
                List.of(entry(null, "alice", null, key))));
    }

    @Test
    void disabledCompatibilityRejectsLegacyFieldsEvenWithId() throws Exception {
        setup(false);
        assertThrows(ValidationErrorsException.class, () -> manager.addUsers("org", "team", false,
                List.of(entry(userId, "old-name", UserType.LOCAL, null))));
        manager.addUsers("org", "team", false, List.of(entry(userId, null, null, null)));
        verify(teams).upsertUser(tx, teamId, userId, TeamRole.MEMBER);
    }
}
