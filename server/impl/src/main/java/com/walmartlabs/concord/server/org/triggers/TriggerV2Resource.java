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
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.org.project.RepositoryUtils.assertRepository;

@Named
@Singleton
@Api(value = "TriggersV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/trigger")
public class TriggerV2Resource implements Resource {

    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final TriggersDao triggersDao;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public TriggerV2Resource(OrganizationDao orgDao,
                             ProjectDao projectDao,
                             TriggersDao triggersDao,
                             ProjectAccessManager projectAccessManager) {

        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.triggersDao = triggersDao;
        this.projectAccessManager = projectAccessManager;
    }

    /**
     * List process trigger definitions for the specified type.
     */
    @GET
    @ApiOperation(value = "List trigger definitions", responseContainer = "list", response = TriggerEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<TriggerEntry> list(@ApiParam @QueryParam("type") @ConcordKey String type,
                                   @ApiParam @QueryParam("orgId") UUID orgId,
                                   @ApiParam @QueryParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @QueryParam("projectId") UUID projectId,
                                   @ApiParam @QueryParam("projectName") @ConcordKey String projectName,
                                   @ApiParam @QueryParam("repoId") UUID repoId,
                                   @ApiParam @QueryParam("repoName") @ConcordKey String repoName) {

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
            ProjectEntry p = assertProject(projectId, ResourceAccessLevel.READER, false);

            if (p == null) {
                throw new IllegalArgumentException("Both organization and project IDs or names are required");
            }

            RepositoryEntry r = assertRepository(p, repoName);
            repoId = r.getId();
        }

        return triggersDao.list(orgId, projectId, repoId, type);
    }


    private ProjectEntry assertProject(UUID projectId, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (projectId == null) {
            throw new ValidationErrorsException("Invalid project ID or name");
        }

        return projectAccessManager.assertAccess(projectId, accessLevel, orgMembersOnly);
    }
}
