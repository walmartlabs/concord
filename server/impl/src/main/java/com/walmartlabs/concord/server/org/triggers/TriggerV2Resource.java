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
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.*;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
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

    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final ProjectAccessManager projectAccessManager;

    private final ProjectRepositoryManager projectRepositoryManager;

    @Inject
    public TriggerV2Resource(OrganizationDao orgDao,
                             ProjectDao projectDao,
                             TriggersDao triggersDao,
                             ProjectAccessManager projectAccessManager,
                             ProjectRepositoryManager projectRepositoryManager) {

        this.orgDao = orgDao;
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

        // TODO: assert org/project access

        if (type != null && (type.isEmpty() || type.length() > 128)) {
            throw new ValidationErrorsException("Invalid type value: " + type);
        }

        if (orgId == null && orgName != null) {
            orgId = orgDao.getId(orgName);
            if (orgId == null) {
                throw new ValidationErrorsException("Organization not found: " + orgName);
            }
        }

        if (projectId == null && projectName != null) {
            if (orgId == null) {
                throw new IllegalArgumentException("Organization ID or name is required");
            }

            projectId = projectDao.getId(orgId, projectName);
            if (projectId == null) {
                throw new ValidationErrorsException("Project not found: " + projectName);
            }
        }

        if (repoId == null && repoName != null) {
            ProjectEntry p = assertProject(projectId);
            RepositoryEntry r = projectRepositoryManager.get(p.getId(), repoName);
            if (r == null) {
                throw new ValidationErrorsException("Repository not found");
            }
            repoId = r.getId();
        }

        return triggersDao.list(orgId, projectId, repoId, type);
    }


    private ProjectEntry assertProject(UUID projectId) {
        if (projectId == null) {
            throw new ValidationErrorsException("Invalid project ID or name");
        }

        return projectAccessManager.assertAccess(projectId, ResourceAccessLevel.READER, false);
    }
}
