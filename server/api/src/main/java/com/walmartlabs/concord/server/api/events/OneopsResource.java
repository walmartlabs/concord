package com.walmartlabs.concord.server.api.events;

import io.swagger.annotations.Api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Api("Events")
@Path("/api/v1/events")
public interface OneopsResource {

    /**
     * Process OneOps event.
     *
     * @param event event's data
     * @return
     */
    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    void event(Map<String, Object> event);
}
