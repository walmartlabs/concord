package com.walmartlabs.concord.agent.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/api/v1/agent")
public interface AgentResource {

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    PingResponse ping();
}
