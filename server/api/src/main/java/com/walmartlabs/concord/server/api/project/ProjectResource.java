package com.walmartlabs.concord.server.api.project;

import com.walmartlabs.concord.common.validation.ConcordId;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/project")
public interface ProjectResource {

    /**
     * Creates a new project.
     *
     * @param request project's data
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateProjectResponse create(@Valid CreateProjectRequest request);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProjectEntry> list(@QueryParam("sortBy") @DefaultValue("name") String sortBy,
                            @QueryParam("asc") @DefaultValue("true") boolean asc);

    /**
     * Updates an existing project.
     *
     * @param id
     * @param request
     * @return
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    UpdateProjectResponse update(@PathParam("id") @ConcordId String id, UpdateProjectRequest request);

    /**
     * Removes a project and all it's resources.
     *
     * @param id
     * @return
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteProjectResponse delete(@PathParam("id") @ConcordId String id);
}
