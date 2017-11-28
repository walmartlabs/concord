package com.walmartlabs.concord.server.api.org.project;

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Projects")
@Path("/api/v1/org")
public interface ProjectResource {

    @POST
    @ApiOperation("Creates a new project or updates an existing one")
    @Path("/{orgName}/project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ProjectOperationResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                            @ApiParam @Valid ProjectEntry entry);

    @GET
    @ApiOperation("Get an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    ProjectEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName);

    @GET
    @ApiOperation("List existing projects")
    @Path("/{orgName}/project")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProjectEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    @GET
    @ApiOperation("Get a project's configuration")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    Object getConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("path") String path);

    @PUT
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                       @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                       @ApiParam @PathParam("path") String path,
                                                       @ApiParam Object data);

    @PUT
    @ApiOperation("Update a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse updateConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                       @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                       @ApiParam Object data);

    @DELETE
    @ApiOperation("Delete a project's configuration parameter")
    @Path("/{orgName}/project/{projectName}/cfg{path: (.*)?}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse deleteConfiguration(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                       @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                       @ApiParam @PathParam("path") String path);

    @DELETE
    @ApiOperation("Delete an existing project")
    @Path("/{orgName}/project/{projectName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                          @ApiParam @PathParam("projectName") @ConcordKey String projectName);

    @POST
    @ApiOperation("Updates the access level for the specified project and team")
    @Path("/{orgName}/project/{projectName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResultResponse updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                                     @ApiParam @Valid ProjectAccessEntry entry);

    /**
     * Encrypts a string with the project's key.
     */
    @POST
    @ApiOperation("Encrypts a string with the project's key")
    @Path("/{orgName}/project/{projectName}/encrypt")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    EncryptValueResponse encrypt(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                 @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                                 @ApiParam String value);
}
