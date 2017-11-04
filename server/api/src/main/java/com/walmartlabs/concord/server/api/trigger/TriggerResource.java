package com.walmartlabs.concord.server.api.trigger;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Api("Triggers")
@Path("/api/v1/triggers")
public interface TriggerResource {

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
    void refresh(@ApiParam @PathParam("projectName") @ConcordKey String projectName,
                 @ApiParam @PathParam("repositoryName") @ConcordKey String repositoryName);
}
