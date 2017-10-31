package com.walmartlabs.concord.server.api.landing;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Api("Landing")
@Path("/api/v1/landing")
public interface LandingResource {

    /**
     * Create or update a project's landing page registration.
     *
     * @param entry landing's data
     * @return
     */
    @POST
    @ApiOperation("Create or update landing")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLandingResponse createOrUpdate(@ApiParam @Valid LandingEntry entry);

    /**
     * Delete project's landing page registration.
     *
     * @param id landing's id
     * @return
     */
    @DELETE
    @ApiOperation("Delete landing")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteLandingResponse delete(@ApiParam @PathParam("id") UUID id);

    /**
     * Lists project's landing page registrations.
     *
     * @return
     */
    @GET
    @ApiOperation("List landings")
    @Produces(MediaType.APPLICATION_JSON)
    List<LandingEntry> list();
}
