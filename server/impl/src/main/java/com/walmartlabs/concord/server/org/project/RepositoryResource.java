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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.repository.RepositoryRefresher;
import com.walmartlabs.concord.server.repository.RepositoryValidationResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Repositories", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class RepositoryResource implements Resource {

    private final OrganizationManager orgManager;
    private final ProjectAccessManager accessManager;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final ProjectRepositoryManager projectRepositoryManager;
    private final RepositoryRefresher repositoryRefresher;

    @Inject
    public RepositoryResource(OrganizationManager orgManager,
                              ProjectAccessManager accessManager,
                              ProjectDao projectDao,
                              RepositoryDao repositoryDao,
                              ProjectRepositoryManager projectRepositoryManager,
                              RepositoryRefresher repositoryRefresher) {

        this.orgManager = orgManager;
        this.accessManager = accessManager;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.projectRepositoryManager = projectRepositoryManager;
        this.repositoryRefresher = repositoryRefresher;
    }

    @POST
    @ApiOperation("Creates a new repository or updates an existing one")
    @Path("/{orgName}/project/{projectName}/repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                 @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                 @ApiParam @Valid RepositoryEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        projectRepositoryManager.createOrUpdate(projectId, entry);
        return new GenericOperationResult(entry.getId() == null ? OperationResult.CREATED : OperationResult.UPDATED);
    }

    @DELETE
    @ApiOperation("Delete an existing repository")
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                         @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        projectRepositoryManager.delete(projectId, repositoryName);

        return new GenericOperationResult(OperationResult.DELETED);
    }

    /**
     * Refresh a local copy of the repository.
     */
    @POST
    @ApiOperation("Refresh a local copy of the repository")
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult refreshRepository(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                    @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName,
                                                    @ApiParam @QueryParam("sync") @DefaultValue("false") boolean sync) {

        repositoryRefresher.refresh(orgName, projectName, repositoryName, sync);
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    /**
     * Validate a repository.
     */
    @POST
    @ApiOperation("Validate an existing repository")
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}/validate")
    @Produces(MediaType.APPLICATION_JSON)
    public RepositoryValidationResponse validateRepository(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                           @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                           @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName) {

        UUID orgId = orgManager.assertAccess(orgName, true).getId();
        UUID projectId = projectDao.getId(orgId, projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, true);

        UUID repoId = repositoryDao.getId(projectId, repositoryName);
        if (repoId == null) {
            throw new ConcordApplicationException("Repository not found: " + repositoryName, Status.NOT_FOUND);
        }

        projectRepositoryManager.validateRepository(projectId, repositoryDao.get(projectId, repoId));

        return new RepositoryValidationResponse(OperationResult.VALIDATED);
    }
}
