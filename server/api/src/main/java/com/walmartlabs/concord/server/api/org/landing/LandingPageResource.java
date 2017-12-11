package com.walmartlabs.concord.server.api.org.landing;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Api(value = "Landing page", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface LandingPageResource {

    /**
     * Create or update a project's landing page registration.
     *
     * @param entry landing's data
     * @return
     */
    @POST
    @Path("/{orgName}/landing_page")
    @ApiOperation("Create or update a project's landing page registration")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLandingResponse createOrUpdate(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                         @ApiParam @Valid LandingEntry entry);

    /**
     * Lists landing page registrations.
     *
     * @return
     */
    @GET
    @Path("/{orgName}/landing_page")
    @ApiOperation("List landing page registrations")
    @Produces(MediaType.APPLICATION_JSON)
    List<LandingEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    /**
     * Refresh all landing page definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh all landing page definitions for all projects")
    @Path("/landing_page/refresh")
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
    @Path("{orgName}/landing_page/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
