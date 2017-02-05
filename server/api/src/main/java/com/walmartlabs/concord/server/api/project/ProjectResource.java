package com.walmartlabs.concord.server.api.project;

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Project")
@Path("/api/v1/project")
public interface ProjectResource {

    /**
     * Creates a new project.
     *
     * @param request project's data
     * @return
     */
    @POST
    @ApiOperation("Create a new project")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateProjectResponse create(@ApiParam @Valid CreateProjectRequest request);

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
    List<ProjectEntry> list(@ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                            @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * Updates an existing project.
     *
     * @param id
     * @param request
     * @return
     */
    @PUT
    @ApiOperation("Update an existing project")
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectResponse update(@ApiParam @PathParam("id") @ConcordId String id,
                                 @ApiParam @Valid UpdateProjectRequest request);

    /**
     * Removes a project and all it's resources.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing project")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectResponse delete(@ApiParam @PathParam("id") @ConcordId String id);
}
