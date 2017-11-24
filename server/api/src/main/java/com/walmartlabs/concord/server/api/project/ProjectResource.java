package com.walmartlabs.concord.server.api.project;

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.team.secret.SecretResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Api("Project")
@Path("/api/v1/project")
public interface ProjectResource {

    /**
     * Creates a new project or updates an existing one.
     *
     * @param request project's data
     * @return
     */
    @POST
    @ApiOperation("Create or update a project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateProjectResponse createOrUpdate(@ApiParam @Valid ProjectEntry request);

    /**
     * Add a new repository to a project.
     *
     * @param projectName
     * @param request
     * @return
     */
    @POST
    @Path("/{projectName}/repository")
    @ApiOperation("Create a new repository")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRepositoryResponse createRepository(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                              @ApiParam @Valid RepositoryEntry request);

    /**
     * Returns an existing project.
     *
     * @param projectName
     * @return
     */
    @GET
    @ApiOperation("Get an existing project")
    @Path("/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    ProjectEntry get(@ApiParam @PathParam("projectName") @ConcordKey String projectName);

    /**
     * Returns an existing repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @GET
    @ApiOperation("Get an existing repository")
    @Path("/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    RepositoryEntry getRepository(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                  @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * List projects.
     *
     * @param sortBy
     * @param asc
     * @return
     */
    @GET
    @ApiOperation("List projects")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProjectEntry> list(@ApiParam @QueryParam("teamId") UUID teamId,
                            @ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                            @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * List repositories.
     *
     * @param projectName
     * @param sortBy
     * @param asc
     * @return
     */
    @GET
    @ApiOperation("List repositories")
    @Path("/{projectName}/repository")
    @Produces(MediaType.APPLICATION_JSON)
    List<RepositoryEntry> listRepositories(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                           @ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                                           @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * Updates an existing project.
     *
     * @param projectName
     * @param request
     * @return
     */
    @PUT
    @ApiOperation("Update an existing project")
    @Path("/{projectName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectResponse update(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                 @ApiParam @Valid UpdateProjectRequest request);

    /**
     * Updates an existing repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @PUT
    @ApiOperation("Update an existing repository")
    @Path("/{projectName}/repository/{repositoryName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateRepositoryResponse updateRepository(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                              @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName,
                                              @ApiParam @Valid RepositoryEntry request);

    /**
     * Returns a project configuration entry for the specified path.
     *
     * @param projectName
     * @param path
     * @return
     */
    @GET
    @ApiOperation("Get project configuration")
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    Object getConfiguration(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("path") String path);


    @PUT
    @ApiOperation("Update project's configuration parameter")
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectConfigurationResponse updateConfiguration(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                           @ApiParam @PathParam("path") String path,
                                                           @ApiParam Object data);

    @PUT
    @ApiOperation("Update project's configuration parameter")
    @Path("/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectConfigurationResponse updateConfiguration(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                           @ApiParam Object data);

    @DELETE
    @ApiOperation("Delete project's configuration parameter")
    @Path("/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectConfigurationResponse deleteConfiguration(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                           @ApiParam @PathParam("path") String path);

    /**
     * Removes a project and all it's resources.
     *
     * @param projectName
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing project")
    @Path("/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectResponse delete(@ApiParam @PathParam("projectName") @ConcordKey String projectName);

    /**
     * Removes a repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing repository")
    @Path("/{projectName}/repository/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteRepositoryResponse deleteRepository(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                              @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * Encrypts a string with the project's key.
     *
     * @deprecated use {@link SecretResource#create(String, MultipartInput)}
     */
    @POST
    @ApiOperation("Encrypts a string with the project's key")
    @Path("/{projectName}/encrypt")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    EncryptValueResponse encrypt(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                 @ApiParam EncryptValueRequest req);
}
