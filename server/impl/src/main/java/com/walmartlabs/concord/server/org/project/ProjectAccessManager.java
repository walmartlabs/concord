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

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Named
public class ProjectAccessManager {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final UserDao userDao;

    @Inject
    public ProjectAccessManager(OrganizationManager orgManager, ProjectDao projectDao, UserDao userDao) {
        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.userDao = userDao;
    }

    public void updateAccessLevel(UUID projectId, UUID teamId, ResourceAccessLevel level) {
        assertProjectAccess(projectId, ResourceAccessLevel.OWNER, true);
        projectDao.upsertAccessLevel(projectId, teamId, level);
    }

    public ProjectEntry assertProjectAccess(UUID orgId, UUID projectId, String projectName, ResourceAccessLevel level, boolean orgMembersOnly) {
        if (projectId == null && projectName == null) {
            throw new ValidationErrorsException("Project ID or name is required");
        }

        if (projectId == null) {
            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new ValidationErrorsException("Project not found: " + projectName);
            }
        }

        return assertProjectAccess(projectId, level, orgMembersOnly);
    }

    @WithTimer
    public ProjectEntry assertProjectAccess(UUID projectId, ResourceAccessLevel level, boolean orgMembersOnly) {
        ProjectEntry e = projectDao.get(projectId);
        if (e == null) {
            throw new ValidationErrorsException("Project not found: " + projectId);
        }

        if (Roles.isAdmin()) {
            // an admin can access any project
            return e;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        if (level == ResourceAccessLevel.READER && (Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            return e;
        } else if (level == ResourceAccessLevel.WRITER && Roles.isGlobalWriter()) {
            return e;
        }

        ProjectOwner owner = e.getOwner();
        if (owner != null && owner.getId().equals(p.getId())) {
            // the owner can do anything with his projects
            return e;
        }

        if (orgMembersOnly && e.getVisibility() == ProjectVisibility.PUBLIC
                && level == ResourceAccessLevel.READER
                && userDao.isInOrganization(p.getId(), e.getOrgId())) {
            // organization members can access any public project in the same organization
            return e;
        }

        OrganizationEntry org = orgManager.assertAccess(e.getOrgId(), false);
        if (ResourceAccessUtils.isSame(p, org.getOwner())) {
            // the org owner can do anything with the org's projects
            return e;
        }

        if (orgMembersOnly || e.getVisibility() != ProjectVisibility.PUBLIC) {
            // we need to check the resource's access level if the access is limited to
            // the organization's members or the project is not public
            if (!projectDao.hasAccessLevel(projectId, p.getId(), ResourceAccessLevel.atLeast(level))) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                        "the necessary access level (" + level + ") to the project: " + e.getName());
            }
        }

        return e;
    }

    public List<ResourceAccessEntry> getResourceAccess(UUID projectId) {
        assertProjectAccess(projectId, ResourceAccessLevel.READER, false);
        return projectDao.getAccessLevel(projectId);
    }

    public void updateAccessLevel(UUID projectId, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        assertProjectAccess(projectId, ResourceAccessLevel.OWNER, true);

        projectDao.tx(tx -> {
            if (isReplace) {
                projectDao.deleteTeamAccess(tx, projectId);
            }

            for (ResourceAccessEntry e : entries) {
                projectDao.upsertAccessLevel(tx, projectId, e.getTeamId(), e.getLevel());
            }
        });
    }
}
