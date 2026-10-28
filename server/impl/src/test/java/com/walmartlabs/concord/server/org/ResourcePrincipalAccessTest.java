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
package com.walmartlabs.concord.server.org;

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreAccessManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreDao;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreEntry;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreVisibility;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourcePrincipalAccessTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final OrganizationManager organizations = mock(OrganizationManager.class);
    private final ProjectDao projects = mock(ProjectDao.class);
    private final UserDao users = mock(UserDao.class);
    private final JsonStoreDao stores = mock(JsonStoreDao.class);
    private final UserManager userManager = mock(UserManager.class);

    private void bind(Object principal, boolean globalReader) {
        ThreadContext.bind(new DefaultSecurityManager());
        Subject subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(principal, "test"));
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(globalReader);
        ThreadContext.bind(subject);
    }

    private SubjectPrincipal sdkUser() {
        return new SubjectPrincipal(userId, "alice", null, new AuthenticationContext("test", null, null));
    }

    private void organizationOwnedByAnotherUser() {
        OrganizationEntry org = mock(OrganizationEntry.class);
        when(org.getOwner()).thenReturn(EntityOwner.builder().id(UUID.randomUUID()).build());
        when(organizations.assertAccess(orgId, false)).thenReturn(org);
    }

    @AfterEach
    void unbind() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void sdkOwnerIsRecognizedByUuidNotName() {
        bind(sdkUser(), false);
        ProjectEntry project = mock(ProjectEntry.class);
        when(project.getOwner()).thenReturn(EntityOwner.builder().id(userId).username("old-name").build());
        ProjectAccessManager access = new ProjectAccessManager(organizations, projects, users, mock(AuditLog.class));
        assertTrue(access.hasAccess(project, ResourceAccessLevel.OWNER, true));
        verifyNoInteractions(projects, organizations);
    }

    @Test
    void privateProjectRequiresCurrentUuidTeamGrant() {
        bind(sdkUser(), false);
        organizationOwnedByAnotherUser();
        ProjectEntry project = mock(ProjectEntry.class);
        when(project.getId()).thenReturn(resourceId);
        when(project.getOrgId()).thenReturn(orgId);
        when(project.getVisibility()).thenReturn(ProjectVisibility.PRIVATE);
        ProjectAccessManager access = new ProjectAccessManager(organizations, projects, users, mock(AuditLog.class));
        assertThrows(UnauthorizedException.class, () -> access.hasAccess(project, ResourceAccessLevel.WRITER, true));
        verify(projects).hasAccessLevel(resourceId, userId, ResourceAccessLevel.atLeast(ResourceAccessLevel.WRITER));
    }

    @Test
    void legacyPrincipalUsesSameStoreAcl() {
        UserEntry user = new UserEntry(userId, "alice", null, null, null, null, null, null, false, null, false);
        bind(new UserPrincipal("test", user), false);
        organizationOwnedByAnotherUser();
        JsonStoreEntry store = JsonStoreEntry.builder().id(resourceId).orgId(orgId).orgName("org")
                .name("store").visibility(JsonStoreVisibility.PRIVATE).build();
        when(stores.hasAccessLevel(resourceId, userId, ResourceAccessLevel.atLeast(ResourceAccessLevel.READER))).thenReturn(true);
        JsonStoreAccessManager access = new JsonStoreAccessManager(userManager, organizations, stores);
        assertTrue(access.hasAccess(store, ResourceAccessLevel.READER, true));
        verify(stores).hasAccessLevel(resourceId, userId, ResourceAccessLevel.atLeast(ResourceAccessLevel.READER));
    }

    @Test
    void teamMembershipIsCheckedAgainstSdkUuid() {
        bind(sdkUser(), false);
        organizationOwnedByAnotherUser();
        TeamDao teams = mock(TeamDao.class);
        TeamManager manager = new TeamManager(teams, mock(OrganizationDao.class), organizations, userManager, mock(AuditLog.class));
        when(teams.isInAnyTeam(orgId, userId, TeamRole.atLeast(TeamRole.MAINTAINER))).thenReturn(true);
        manager.assertAccess(orgId, TeamRole.MAINTAINER);
        verify(teams).isInAnyTeam(orgId, userId, TeamRole.atLeast(TeamRole.MAINTAINER));
    }

    @Test
    void credentialOnlyGlobalReaderRetainsStoreReadAccess() {
        bind("credential", true);
        JsonStoreEntry store = JsonStoreEntry.builder().id(resourceId).orgId(orgId).orgName("org")
                .name("store").visibility(JsonStoreVisibility.PRIVATE).build();
        JsonStoreAccessManager access = new JsonStoreAccessManager(userManager, organizations, stores);
        assertTrue(access.hasAccess(store, ResourceAccessLevel.READER, true));
        verifyNoInteractions(stores, organizations);
    }
}
