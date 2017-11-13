package com.walmartlabs.concord.server.api.trigger;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api("Triggers")
@Path("/api/v1/triggers")
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
    @Path("/refresh/{projectName}/{repositoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    List<TriggerEntry> list(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                            @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);

    /**
     * Refresh process trigger definitions for the specified project and repository.
     *
     * @param projectName
     * @param repositoryName
     * @return
     */
    @POST
    @ApiOperation("Refresh trigger definitions")
    @Path("/refresh/{projectName}/{repositoryName}")
    Response refresh(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                     @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
