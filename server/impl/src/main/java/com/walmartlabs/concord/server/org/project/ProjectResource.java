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

import com.google.common.base.Splitter;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.*;

@Named
@Singleton
@Api(value = "Projects", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class ProjectResource implements Resource {

    private final OrganizationManager orgManager;
    private final ProjectDao projectDao;
    private final ProjectManager projectManager;
    private final ProjectAccessManager accessManager;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;
    private final EncryptedProjectValueManager encryptedValueManager;

    @Inject
    public ProjectResource(OrganizationManager orgManager,
                           ProjectDao projectDao,
                           ProjectManager projectManager,
                           ProjectAccessManager accessManager,
                           OrganizationDao orgDao,
                           TeamDao teamDao,
                           EncryptedProjectValueManager encryptedValueManager) {

        this.orgManager = orgManager;
        this.projectDao = projectDao;
        this.projectManager = projectManager;
        this.accessManager = accessManager;
        this.encryptedValueManager = encryptedValueManager;
        this.orgDao = orgDao;
        this.teamDao = teamDao;
    }

    @POST
    @ApiOperation("Creates a new project or updates an existing one")
    @Path("/{orgName}/project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public ProjectOperationResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                   @ApiParam @Valid ProjectEntry entry) {

        entry = normalize(entry);

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = entry.getId();
        if (projectId == null) {
            projectId = projectDao.getId(org.getId(), entry.getName());
        }

        if (projectId != null) {
            projectManager.update(projectId, entry);
            return new ProjectOperationResponse(projectId, OperationResult.UPDATED);
        }

        projectId = projectManager.insert(org.getId(), org.getName(), entry);
        return new ProjectOperationResponse(projectId, OperationResult.CREATED);
    }

    @GET
    @ApiOperation("Get an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public ProjectEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        return projectManager.get(projectId);
    }

    @GET
    @ApiOperation(value = "List existing projects", responseContainer = "list", response = ProjectEntry.class)
    @Path("/{orgName}/project")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public List<ProjectEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return projectManager.list(org.getId());
    }

    @GET
    @ApiOperation("Get a project's configuration")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public Object getConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                   @ApiParam @PathParam("path") String path) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, false);

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
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @SuppressWarnings("unchecked")
    public GenericOperationResult updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                      @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                      @ApiParam @PathParam("path") String path,
                                                      @ApiParam Object data) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);

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
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                      @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                      @ApiParam Object data) {

        return updateConfiguration(orgName, projectName, "/", data);
    }

    @DELETE
    @ApiOperation("Delete a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult deleteConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                      @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                      @ApiParam @PathParam("path") String path) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        // TODO move to ProjectManager
        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.WRITER, true);

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
    @ApiOperation("Delete an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @PathParam("projectName") @ConcordKey String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        projectManager.delete(projectId);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @GET
    @ApiOperation("Get project team access")
    @Path("/{orgName}/project/{projectName}/access")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public List<ResourceAccessEntry> getAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("projectName") @ConcordKey String projectName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }
        return accessManager.getResourceAccess(projectId);
    }

    @POST
    @ApiOperation("Updates the access level for the specified project and team")
    @Path("/{orgName}/project/{projectName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                    @ApiParam @Valid ResourceAccessEntry entry) {

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
    @ApiOperation("Updates the access level for the specified project and team")
    @Path("/{orgName}/project/{projectName}/access/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                    @ApiParam @Valid Collection<ResourceAccessEntry> entries) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        accessManager.updateAccessLevel(projectId, entries, true);

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @POST
    @ApiOperation("Encrypts a string with the project's key")
    @Path("/{orgName}/project/{projectName}/encrypt")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public EncryptValueResponse encrypt(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                        @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                        @ApiParam String value) {

        if (value == null) {
            throw new ValidationErrorsException("Value is required");
        }

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID projectId = projectDao.getId(org.getId(), projectName);
        if (projectId == null) {
            throw new ConcordApplicationException("Project not found: " + projectName, Status.NOT_FOUND);
        }

        accessManager.assertProjectAccess(projectId, ResourceAccessLevel.READER, true);

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

    private static ProjectEntry normalize(ProjectEntry e) {
        Map<String, RepositoryEntry> repos = e.getRepositories();
        if (repos != null) {
            Map<String, RepositoryEntry> m = new HashMap<>(repos);

            repos.forEach((k, v) -> {
                if (v.getName() == null) {
                    RepositoryEntry r = new RepositoryEntry(k, v);
                    m.put(k, r);
                }
            });

            e = ProjectEntry.replace(e, m);
        }

        return e;
    }
}
