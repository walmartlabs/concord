package com.walmartlabs.concord.server.events;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;

@Path("/events/github")
public interface GithubEventResource {

    @POST
    @Path("/push/{projectId}/{repoName}")
    @Consumes(MediaType.APPLICATION_JSON)
    String push(@PathParam("projectId") UUID projectId,
                @PathParam("repoName") String repoName, Map<String, Object> event);
}
