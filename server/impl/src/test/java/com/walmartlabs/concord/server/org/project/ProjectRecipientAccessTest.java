package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRecipientAccessTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final OrganizationManager orgs = mock(OrganizationManager.class);
    private final ProjectDao projects = mock(ProjectDao.class);
    private final UserDao users = mock(UserDao.class);
    private final UserEntry user = mock(UserEntry.class);
    private final ProjectEntry project = mock(ProjectEntry.class);
    private final OrganizationEntry org = mock(OrganizationEntry.class);
    private final ProjectAccessManager access = new ProjectAccessManager(orgs, projects, users, mock(AuditLog.class));

    @BeforeEach
    void setup() {
        when(project.getId()).thenReturn(projectId);
        when(project.getOrgId()).thenReturn(orgId);
        when(project.getVisibility()).thenReturn(ProjectVisibility.PRIVATE);
        when(orgs.assertAccess(orgId, false)).thenReturn(org);
        when(users.get(userId)).thenReturn(user);
    }

    @Test
    void publicProjectsRequireMembershipInTheirOwnOrganization() {
        when(project.getVisibility()).thenReturn(ProjectVisibility.PUBLIC);
        assertFalse(access.hasUserReadAccess(project, userId));
        when(users.isInOrganization(userId, orgId)).thenReturn(true);
        assertTrue(access.hasUserReadAccess(project, userId));
    }

    @Test
    void privateProjectRecipientsRequireTheirOwnPermission() {
        assertFalse(access.hasUserReadAccess(project, userId));
        when(projects.hasAccessLevel(projectId, userId, ResourceAccessLevel.atLeast(ResourceAccessLevel.READER))).thenReturn(true);
        assertTrue(access.hasUserReadAccess(project, userId));
    }

    @Test
    void projectAndOrganizationOwnersAreReadersWithoutATeamGrant() {
        var owner = mock(EntityOwner.class);
        when(owner.id()).thenReturn(userId);
        when(project.getOwner()).thenReturn(owner);
        assertTrue(access.hasUserReadAccess(project, userId));
        when(project.getOwner()).thenReturn(null);
        when(org.getOwner()).thenReturn(owner);
        assertTrue(access.hasUserReadAccess(project, userId));
    }

    @Test
    void globalRolesAreEvaluatedForTheRecipientNotTheCaller() {
        for (var role : Set.of(Roles.ADMIN, Roles.SYSTEM_READER, Roles.SYSTEM_WRITER)) {
            when(user.getRoles()).thenReturn(Set.of(new RoleEntry(UUID.randomUUID(), role, Set.of())));
            assertTrue(access.hasUserReadAccess(project, userId));
        }
    }

    @Test
    void missingAndDisabledRecipientsAreDeniedEvenWithRoles() {
        when(user.getRoles()).thenReturn(Set.of(new RoleEntry(UUID.randomUUID(), Roles.ADMIN, Set.of())));
        when(user.isDisabled()).thenReturn(true);
        assertFalse(access.hasUserReadAccess(project, userId));
        when(users.get(userId)).thenReturn(null);
        assertFalse(access.hasUserReadAccess(project, userId));
    }
}
