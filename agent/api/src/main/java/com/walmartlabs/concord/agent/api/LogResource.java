package com.walmartlabs.concord.agent.api;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/v1/log")
public interface LogResource {

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/{id}")
    Response stream(@PathParam("id") @NotNull String id);
}
