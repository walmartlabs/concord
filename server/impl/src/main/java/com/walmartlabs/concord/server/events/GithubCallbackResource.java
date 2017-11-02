package com.walmartlabs.concord.server.events;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/events/github")
public interface GithubCallbackResource {

    @POST
    @Path("/push/{repoId}")
    @Consumes(MediaType.APPLICATION_JSON)
    String push(@PathParam("repoId") UUID repoId);
}
