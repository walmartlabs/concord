package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProjectAccessManager {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final UserDao userDao;
    private final AuditLog auditLog;

    @Inject
    public ProjectAccessManager(OrganizationManager orgManager, ProjectDao projectDao, UserDao userDao, AuditLog auditLog) {
        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.userDao = userDao;
        this.auditLog = auditLog;
    }

    public void updateAccessLevel(UUID projectId, UUID teamId, ResourceAccessLevel level) {
        assertAccess(projectId, ResourceAccessLevel.OWNER, true);
        projectDao.upsertAccessLevel(projectId, teamId, level);

        addAuditLog(projectId, Collections.singletonList(new ResourceAccessEntry(teamId, null, null, level)), false);
    }

    public ProjectEntry assertAccess(UUID orgId, UUID projectId, String projectName, ResourceAccessLevel level, boolean orgMembersOnly) {
        if (projectId == null && projectName == null) {
            throw new ValidationErrorsException("Project ID or name is required");
        }

        if (projectId == null) {
            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new ValidationErrorsException("Project not found: " + projectName);
            }
        }

        return assertAccess(projectId, level, orgMembersOnly);
    }

    public ProjectEntry assertAccess(UUID projectId, ResourceAccessLevel level, boolean orgMembersOnly) {
        return projectDao.txResult(tx -> assertAccess(tx, projectId, level, orgMembersOnly));
    }

    public ProjectEntry assertAccess(DSLContext tx, UUID projectId, ResourceAccessLevel level, boolean orgMembersOnly) {
        ProjectEntry project = projectDao.get(tx, projectId);
        if (project == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        if (!hasAccess(project, level, orgMembersOnly)) {
            UserPrincipal p = UserPrincipal.getCurrent();
            throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                    "the necessary access level (" + level + ") to the project: " + project.getName());
        }

        return project;
    }

    @WithTimer
    public boolean hasAccess(ProjectEntry project, ResourceAccessLevel level, boolean orgMembersOnly) {
        if (Roles.isAdmin()) {
            // an admin can access any project
            return true;
        }

        UserPrincipal principal = UserPrincipal.assertCurrent();

        if (level == ResourceAccessLevel.READER && (Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            return true;
        } else if (level == ResourceAccessLevel.WRITER && Roles.isGlobalWriter()) {
            return true;
        }

        EntityOwner owner = project.getOwner();
        if (ResourceAccessUtils.isSame(principal, owner)) {
            // the owner can do anything with his projects
            return true;
        }

        if (orgMembersOnly && project.getVisibility() == ProjectVisibility.PUBLIC
                && level == ResourceAccessLevel.READER
                && userDao.isInOrganization(principal.getId(), project.getOrgId())) {
            // organization members can READ any public project in the same organization
            return true;
        }

        OrganizationEntry org = orgManager.assertAccess(project.getOrgId(), false);
        if (ResourceAccessUtils.isSame(principal, org.getOwner())) {
            // the org owner can do anything with the org's projects
            return true;
        }

        if (orgMembersOnly || project.getVisibility() != ProjectVisibility.PUBLIC) {
            // we need to check the resource's access level if the access is limited to
            // the organization's members or the project is not public
            if (!projectDao.hasAccessLevel(project.getId(), principal.getId(), ResourceAccessLevel.atLeast(level))) {
                throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                        "the necessary access level (" + level + ") to the project: " + project.getName());
            }
        }

        return true;
    }

    public List<ResourceAccessEntry> getResourceAccess(UUID projectId) {
        assertAccess(projectId, ResourceAccessLevel.READER, false);
        return projectDao.getAccessLevel(projectId);
    }

    public void updateAccessLevel(UUID projectId, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        assertAccess(projectId, ResourceAccessLevel.OWNER, true);

        projectDao.tx(tx -> {
            if (isReplace) {
                projectDao.deleteTeamAccess(tx, projectId);
            }

            for (ResourceAccessEntry e : entries) {
                projectDao.upsertAccessLevel(tx, projectId, e.getTeamId(), e.getLevel());
            }
        });

        addAuditLog(projectId, entries, isReplace);
    }

    /**
     * @return {@code true} if the current user is a member of a team that has
     * access to the specified project.
     */
    public boolean isTeamMember(UUID projectId) {
        UserPrincipal principal = UserPrincipal.assertCurrent();
        return projectDao.hasAccessLevel(projectId, principal.getId(), ResourceAccessLevel.atLeast(ResourceAccessLevel.READER));
    }

    private void addAuditLog(UUID projectId, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        List<ImmutableMap<String, ? extends Serializable>> teams = entries.stream()
                .map(e -> ImmutableMap.of("id", e.getTeamId(), "level", e.getLevel()))
                .collect(Collectors.toList());

        auditLog.add(AuditObject.PROJECT, AuditAction.UPDATE)
                .field("projectId", projectId)
                .field("access", ImmutableMap.of(
                        "replace", isReplace,
                        "teams", teams))
                .log();
    }
}
