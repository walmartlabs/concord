package com.walmartlabs.concord.server.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/api/v1/server")
public interface ServerResource {

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    PingResponse ping();
}
