package com.walmartlabs.concord.server.api.landing;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Api("Landing page")
@Path("/api/v1/landing_page")
public interface LandingPageResource {

    /**
     * Create or update a project's landing page registration.
     *
     * @param entry landing's data
     * @return
     */
    @POST
    @ApiOperation("Create or update a project's landing page registration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLandingResponse createOrUpdate(@ApiParam @Valid LandingEntry entry);

    /**
     * Delete an existing project's landing page registration.
     *
     * @param id landing's id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing project's landing page registration")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteLandingResponse delete(@ApiParam @PathParam("id") UUID id);

    /**
     * Lists landing page registrations.
     *
     * @return
     */
    @GET
    @ApiOperation("List landing page registrations")
    @Produces(MediaType.APPLICATION_JSON)
    List<LandingEntry> list();

    /**
     * Refresh all landing page definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh all landing page definitions for all projects")
    @Path("/refresh")
    Response refreshAll();

    /**
     * Refresh landing page definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh landing page definitions for the specified project and repository")
    @Path("/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                 @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
