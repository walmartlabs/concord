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

import com.google.common.base.Splitter;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/org")
@Tag(name = "Projects")
public class ProjectResource implements Resource {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final ProjectManager projectManager;
    private final ProjectAccessManager accessManager;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;
    private final KvDao kvDao;
    private final EncryptedProjectValueManager encryptedValueManager;

    private final ProjectRepositoryManager projectRepositoryManager;

    @Inject
    public ProjectResource(OrganizationManager orgManager,
                           ProjectDao projectDao,
                           ProjectManager projectManager,
                           ProjectAccessManager accessManager,
                           OrganizationDao orgDao,
                           TeamDao teamDao,
                           KvDao kvDao,
                           EncryptedProjectValueManager encryptedValueManager,
                           ProjectRepositoryManager projectRepositoryManager) {

        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.projectManager = projectManager;
        this.accessManager = accessManager;
        this.kvDao = kvDao;
        this.encryptedValueManager = encryptedValueManager;
        this.orgDao = orgDao;
        this.teamDao = teamDao;
        this.projectRepositoryManager = projectRepositoryManager;
    }

    @POST
    @Path("/{orgName}/project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Creates a new project or updates an existing one", operationId = "createOrUpdateProject")
    public ProjectOperationResponse createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                   @Valid ProjectEntry entry) {

        ProjectOperationResult result = projectManager.createOrUpdate(orgName, entry);
        return new ProjectOperationResponse(result.projectId(), result.result());
    }

    /**
     * @deprecated use {@link ProjectResourceV2#get(String, String)}
     */
    @Deprecated
    @GET
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public ProjectEntry get(@PathParam("orgName") @ConcordKey String orgName,
                            @PathParam("projectName") @ConcordKey String projectName) {

        ProjectEntry p = projectManager.get(orgName, projectName);
        List<RepositoryEntry> repositories = projectRepositoryManager.list(p.getId());
        return ProjectEntry.replace(p, repositories.stream().collect(Collectors.toMap(RepositoryEntry::getName, r -> r)));
    }

    @GET
    @Path("/{orgName}/project")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "List existing projects", operationId = "findProjects")
    public List<ProjectEntry> find(@PathParam("orgName") @ConcordKey String orgName,
                                   @QueryParam("offset") int offset,
                                   @QueryParam("limit") int limit,
                                   @QueryParam("filter") String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return projectManager.list(org.getId(), offset, limit, filter);
    }

    @GET
    @Path("/{orgName}/project/{projectName}/kv")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List KV", operationId = "listProjectKv")
    public List<KvEntry> findKV(@PathParam("orgName") @ConcordKey String orgName,
                                @PathParam("projectName") @ConcordKey String projectName,
                                @QueryParam("offset") int offset,
                                @QueryParam("limit") int limit,
                                @QueryParam("filter") String filter) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        ProjectEntry project = accessManager.assertAccess(org.getId(), null, projectName, ResourceAccessLevel.READER, false);

        return kvDao.list(project.getId(), offset, limit, filter);
    }

    /**
     * Get the KV capacity.
     */
    @GET
    @Path("/{orgName}/project/{projectName}/kv/capacity")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get a project's KV capacity", operationId = "getProjectKVCapacity")
    public ProjectKvCapacity getCapacity(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("projectName") @ConcordKey String projectName) {

        return projectManager.getKvCapacity(orgName, projectName);
    }

    @GET
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Get a project's configuration", operationId = "getProjectConfiguration")
    public Object getConfiguration(@PathParam("orgName") @ConcordKey String orgName,
                                   @PathParam("projectName") @ConcordKey String projectName,
                                   @PathParam("path") String path) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertAccess(projectId, ResourceAccessLevel.READER, false);

        String[] ps = cfgPath(path);
        Object v = projectDao.getConfigurationValue(projectId, ps);

        if (v == null) {
            if (path == null || path.isEmpty()) {
                return Collections.emptyMap();
            }

            throw new ConcordApplicationException("Value not found: " + path, Status.NOT_FOUND);
        }

        return v;
    }

    @PUT
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Update a project's configuration parameter", operationId = "updateProjectConfiguration")
    @SuppressWarnings("unchecked")
    public GenericOperationResult updateConfiguration(@PathParam("orgName") @ConcordKey String orgName,
                                                      @PathParam("projectName") @ConcordKey String projectName,
                                                      @PathParam("path") String path,
                                                      Object data) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);

        Map<String, Object> cfg = projectDao.getConfiguration(projectId);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        String[] ps = cfgPath(path);
        if (ps.length < 1) {
            if (!(data instanceof Map)) {
                throw new ValidationErrorsException("Expected a JSON object: " + data);
            }
            cfg = (Map<String, Object>) data;
        } else {
            ConfigurationUtils.set(cfg, data, ps);
        }

        Map<String, Object> newCfg = cfg;
        projectDao.updateCfg(projectId, newCfg);
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @PUT
    @Path("/{orgName}/project/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Update a project's configuration parameter", operationId = "updateProjectConfiguration")
    public GenericOperationResult updateConfiguration(@PathParam("orgName") @ConcordKey String orgName,
                                                      @PathParam("projectName") @ConcordKey String projectName,
                                                      Object data) {

        return updateConfiguration(orgName, projectName, "/", data);
    }

    @DELETE
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Delete a project's configuration parameter", operationId = "deleteProjectConfiguration")
    public GenericOperationResult deleteConfiguration(@PathParam("orgName") @ConcordKey String orgName,
                                                      @PathParam("projectName") @ConcordKey String projectName,
                                                      @PathParam("path") String path) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);

        Map<String, Object> cfg = projectDao.getConfiguration(projectId);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        String[] ps = cfgPath(path);
        if (ps.length == 0) {
            cfg = null;
        } else {
            ConfigurationUtils.delete(cfg, ps);
        }

        Map<String, Object> newCfg = cfg;
        projectDao.updateCfg(projectId, newCfg);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @DELETE
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Delete an existing project", operationId = "deleteProject")
    public GenericOperationResult delete(@PathParam("orgName") @ConcordKey String orgName,
                                         @PathParam("projectName") @ConcordKey String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        projectManager.delete(projectId);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @GET
    @Path("/{orgName}/project/{projectName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Get project team access", operationId = "getProjectAccessLevel")
    public List<ResourceAccessEntry> getAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("projectName") @ConcordKey String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }
        return accessManager.getResourceAccess(projectId);
    }

    @POST
    @Path("/{orgName}/project/{projectName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified project and team", operationId = "updateProjectAccessLevel")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("projectName") @ConcordKey String projectName,
                                                    @Valid ResourceAccessEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);

        accessManager.updateAccessLevel(projectId, teamId, entry.getLevel());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @POST
    @Path("/{orgName}/project/{projectName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified project and team", operationId = "updateProjectAccessLevelBulk")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("projectName") @ConcordKey String projectName,
                                                    @Valid Collection<ResourceAccessEntry> entries) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        if (entries == null) {
            throw new ConcordApplicationException("List of teams is null.", Status.BAD_REQUEST);
        }

        accessManager.updateAccessLevel(projectId, entries, true);

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @POST
    @Path("/{orgName}/project/{projectName}/encrypt")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Encrypts a string with the project's key")
    public EncryptValueResponse encrypt(@PathParam("orgName") @ConcordKey String orgName,
                                        @PathParam("projectName") @ConcordKey String projectName,
                                        String value) {

        if (value == null) {
            throw new ValidationErrorsException("Value is required");
        }

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        accessManager.assertAccess(projectId, ResourceAccessLevel.READER, true);

        byte[] input = value.getBytes();
        byte[] result = encryptedValueManager.encrypt(projectId, input);

        return new EncryptValueResponse(result);
    }

    private static String[] cfgPath(String s) {
        if (s == null) {
            return new String[0];
        }

        List<String> l = Splitter.on("/").omitEmptyStrings().splitToList(s);
        return l.toArray(new String[0]);
    }
}
