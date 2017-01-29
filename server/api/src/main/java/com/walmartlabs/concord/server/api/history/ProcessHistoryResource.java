package com.walmartlabs.concord.server.api.history;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/history")
public interface ProcessHistoryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProcessHistoryEntry> list(
            @QueryParam("sortBy") @DefaultValue("lastUpdateDt") String sortBy,
            @QueryParam("asc") @DefaultValue("true") boolean asc,
            @QueryParam("limit") @DefaultValue("30") int limit);
}
