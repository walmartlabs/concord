package com.walmartlabs.concord.server.org.triggers;

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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.*;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/trigger")
@Tag(name = "TriggersV2")
public class TriggerV2Resource implements Resource {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final ProjectAccessManager projectAccessManager;
    private final ProjectRepositoryManager projectRepositoryManager;

    @Inject
    public TriggerV2Resource(OrganizationManager orgManager,
                             ProjectDao projectDao,
                             TriggersDao triggersDao,
                             ProjectAccessManager projectAccessManager,
                             ProjectRepositoryManager projectRepositoryManager) {

        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.projectAccessManager = projectAccessManager;
        this.projectRepositoryManager = projectRepositoryManager;
    }

    /**
     * List process trigger definitions for the specified type.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List trigger definitions", operationId = "listTriggersV2")
    public List<TriggerEntry> list(@QueryParam("type") @ConcordKey String type,
                                   @QueryParam("orgId") UUID orgId,
                                   @QueryParam("orgName") @ConcordKey String orgName,
                                   @QueryParam("projectId") UUID projectId,
                                   @QueryParam("projectName") @ConcordKey String projectName,
                                   @QueryParam("repoId") UUID repoId,
                                   @QueryParam("repoName") @ConcordKey String repoName) {

        if (type != null && (type.isEmpty() || type.length() > 128)) {
            throw new ValidationErrorsException("Invalid type value: " + type);
        }

        if (orgId == null && orgName == null) {
            throw new ValidationErrorsException("Organization ID or name is required");
        }

        // Assert org exists and the caller has at least read access to it.
        OrganizationEntry org = orgManager.assertAccess(orgId, orgName, false);
        orgId = org.getId();

        if (projectId == null && projectName != null) {
            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new ValidationErrorsException("Project not found: " + projectName);
            }
        }

        // Assert the caller has read access to the project when one is in scope.
        ProjectEntry project = null;
        if (projectId != null) {
            project = projectAccessManager.assertAccess(projectId, ResourceAccessLevel.READER, false);
        }

        if (repoId == null && repoName != null) {
            if (project == null) {
                throw new ValidationErrorsException("Project ID or name is required to look up a repository by name");
            }
            RepositoryEntry r = projectRepositoryManager.get(project.getId(), repoName);
            if (r == null) {
                throw new ValidationErrorsException("Repository not found: " + repoName);
            }
            repoId = r.getId();
        }

        // For org-wide queries (no specific project), filter to projects the caller can see.
        // Admins and global readers/writers pass null to skip per-user filtering.
        UUID currentUserId = null;
        if (projectId == null && !(Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            currentUserId = UserPrincipal.assertCurrent().getId();
        }

        return triggersDao.list(orgId, projectId, repoId, type, currentUserId);
    }
}
