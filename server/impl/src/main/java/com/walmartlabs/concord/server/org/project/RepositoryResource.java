package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.repository.RepositoryRefresher;
import com.walmartlabs.concord.server.repository.RepositoryValidationResponse;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.UUID;

@Path("/api/v1/org")
@Tag(name = "Repositories")
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

    @GET
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Get an existing repository", operationId = "getRepository")
    public RepositoryEntry get(@PathParam("orgName") @ConcordKey String orgName,
                               @PathParam("projectName") @ConcordKey String projectName,
                               @PathParam("repositoryName") @ConcordKey String repositoryName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return projectRepositoryManager.get(org.getId(), projectName, repositoryName);
    }

    @GET
    @Path("/{orgName}/project/{projectName}/repository")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "List existing repositories", operationId = "listRepositories")
    public List<RepositoryEntry> find(@PathParam("orgName") @ConcordKey String orgName,
                                      @PathParam("projectName") @ConcordKey String projectName,
                                      @QueryParam("offset") int offset,
                                      @QueryParam("limit") int limit,
                                      @QueryParam("filter") String filter) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return projectRepositoryManager.list(org.getId(), projectName, offset, limit, filter);
    }

    @POST
    @Path("/{orgName}/project/{projectName}/repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Creates a new repository or updates an existing one", operationId = "createOrUpdateRepository")
    public GenericOperationResult createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                 @PathParam("projectName") @ConcordKey String projectName,
                                                 @Valid RepositoryEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        projectRepositoryManager.createOrUpdate(projectId, entry);
        return new GenericOperationResult(entry.getId() == null ? OperationResult.CREATED : OperationResult.UPDATED);
    }

    @DELETE
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete an existing repository", operationId = "deleteRepository")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("projectName") @ConcordKey String projectName,
                                         @PathParam("repositoryName") @ConcordKey String repositoryName) {

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
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Refresh a local copy of the repository")
    public GenericOperationResult refreshRepository(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("projectName") @ConcordKey String projectName,
                                                    @PathParam("repositoryName") @ConcordKey String repositoryName,
                                                    @QueryParam("sync") @DefaultValue("false") boolean sync) {

        repositoryRefresher.refresh(orgName, projectName, repositoryName);
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    /**
     * Validate a repository.
     */
    @POST
    @Path("/{orgName}/project/{projectName}/repository/{repositoryName}/validate")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Validate an existing repository")
    public RepositoryValidationResponse validateRepository(@PathParam("orgName") @ConcordKey String orgName,
                                                           @PathParam("projectName") @ConcordKey String projectName,
                                                           @PathParam("repositoryName") @ConcordKey String repositoryName) {

        UUID orgId = orgManager.assertAccess(orgName, true).getId();
        UUID projectId = projectDao.getId(orgId, projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        accessManager.assertAccess(projectId, ResourceAccessLevel.READER, true);

        RepositoryEntry repo = repositoryDao.get(projectId, repositoryName);
        if (repo == null) {
            throw new ConcordApplicationException("Repository not found: " + repositoryName, Status.NOT_FOUND);
        }

        ProjectValidator.Result result = projectRepositoryManager.validateRepository(orgId, repo);

        return new RepositoryValidationResponse(result.isValid(), OperationResult.VALIDATED, result.getErrors(), result.getWarnings());
    }
}
