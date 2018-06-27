package com.walmartlabs.concord.server.api.project;

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
import com.walmartlabs.concord.server.api.org.project.EncryptValueResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretResource;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

/**
 * @deprecated prefer {@link com.walmartlabs.concord.server.api.org.project.ProjectResource}
 */
@Path("/api/v1/project")
@Deprecated
public interface ProjectResource {

    /**
     * Creates a new project or updates an existing one.
     *
     * @param request project's data
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateProjectResponse createOrUpdate(@Valid ProjectEntry request);

    /**
     * Add a new repository to a project.
     *
     * @param projectName
     * @param request
     * @return
     */
    @POST
    @Path("/{projectName}/repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRepositoryResponse createRepository(@PathParam("projectName") @ConcordKey String projectName,
                                              @Valid RepositoryEntry request);

    /**
     * Returns an existing project.
     *
     * @param projectName
     * @return
     */
    @GET
    @Path("/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    ProjectEntry get(@PathParam("projectName") @ConcordKey String projectName);

    /**
     * Returns an existing repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @GET
    @Path("/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    RepositoryEntry getRepository(@PathParam("projectName") @ConcordKey String projectName,
                                  @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * List projects.
     *
     * @param sortBy
     * @param asc
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProjectEntry> list(@QueryParam("orgId") UUID orgId,
                            @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                            @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * List repositories.
     *
     * @param projectName
     * @param sortBy
     * @param asc
     * @return
     */
    @GET
    @Path("/{projectName}/repository")
    @Produces(MediaType.APPLICATION_JSON)
    List<RepositoryEntry> listRepositories(@PathParam("projectName") @ConcordKey String projectName,
                                           @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                                           @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * Updates an existing repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @PUT
    @Path("/{projectName}/repository/{repositoryName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateRepositoryResponse updateRepository(@PathParam("projectName") @ConcordKey String projectName,
                                              @PathParam("repositoryName") @ConcordKey String repositoryName,
                                              @Valid RepositoryEntry request);

    /**
     * Returns a project configuration entry for the specified path.
     *
     * @param projectName
     * @param path
     * @return
     */
    @GET
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    Object getConfiguration(@PathParam("projectName") @ConcordKey String projectName,
                            @PathParam("path") String path);


    @PUT
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectConfigurationResponse updateConfiguration(@PathParam("projectName") @ConcordKey String projectName,
                                                           @PathParam("path") String path,
                                                           Object data);

    @PUT
    @Path("/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectConfigurationResponse updateConfiguration(@PathParam("projectName") @ConcordKey String projectName,
                                                           Object data);

    @DELETE
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectConfigurationResponse deleteConfiguration(@PathParam("projectName") @ConcordKey String projectName,
                                                           @PathParam("path") String path);

    /**
     * Removes a project and all it's resources.
     *
     * @param projectName
     * @return
     */
    @DELETE
    @Path("/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectResponse delete(@PathParam("projectName") @ConcordKey String projectName);

    /**
     * Removes a repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @DELETE
    @Path("/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteRepositoryResponse deleteRepository(@PathParam("projectName") @ConcordKey String projectName,
                                              @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * Encrypts a string with the project's key.
     *
     * @deprecated use {@link SecretResource#create(String, MultipartInput)}
     */
    @POST
    @Path("/{projectName}/encrypt")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    EncryptValueResponse encrypt(@PathParam("projectName") @ConcordKey String projectName,
                                 EncryptValueRequest req);
}
