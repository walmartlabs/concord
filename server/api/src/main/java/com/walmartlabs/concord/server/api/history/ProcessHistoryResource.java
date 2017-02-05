package com.walmartlabs.concord.server.api.history;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Process history")
@Path("/api/v1/history")
public interface ProcessHistoryResource {

    @GET
    @ApiOperation("List")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessHistoryEntry> list(
            @ApiParam @QueryParam("sortBy") @DefaultValue("lastUpdateDt") String sortBy,
            @ApiParam @QueryParam("asc") @DefaultValue("false") boolean asc,
            @ApiParam @QueryParam("limit") @DefaultValue("30") int limit);
}
