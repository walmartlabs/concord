package com.walmartlabs.concord.server.events;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/events/github")
public interface GithubCallbackResource {

    @POST
    @Path("/push/{projectName}/{repositoryName}")
    @Consumes(MediaType.APPLICATION_JSON)
    String push(@PathParam("projectName") String projectName,
                @PathParam("repositoryName") String repositoryName);
}
