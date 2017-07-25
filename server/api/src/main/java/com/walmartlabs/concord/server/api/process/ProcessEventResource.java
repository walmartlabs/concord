package com.walmartlabs.concord.server.api.process;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Api("Event")
@Path("/api/v1/process")
public interface ProcessEventResource {

    /**
     * List process events.
     *
     * @param processInstanceId
     * @return
     */
    @GET
    @ApiOperation("List process events")
    @Path("/{processInstanceId}/event")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessEventEntry> list(@ApiParam @PathParam("processInstanceId") UUID processInstanceId);
}
