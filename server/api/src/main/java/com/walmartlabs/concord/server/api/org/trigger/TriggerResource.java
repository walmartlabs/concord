package com.walmartlabs.concord.server.api.org.trigger;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(value = "Triggers", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface TriggerResource {

    /**
     * List process trigger definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @GET
    @ApiOperation("List trigger definitions")
    @Path("/{orgName}/trigger/refresh/{projectName}/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    List<TriggerEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                            @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * Refresh process trigger definitions for all projects.
     *
     * @return
     */
    @POST
    @ApiOperation("Refresh trigger definitions for all projects")
    @Path("/trigger/refresh")
    Response refreshAll();

    /**
     * Refresh process trigger definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh trigger definitions for the specified project and repository")
    @Path("/{orgName}/trigger/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("projectName") @ConcordKey String projectName,
                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
