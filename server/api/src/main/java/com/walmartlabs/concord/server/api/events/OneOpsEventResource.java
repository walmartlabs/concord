package com.walmartlabs.concord.server.api.events;

import io.swagger.annotations.Api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Map;

@Api("Events")
@Path("/api/v1/events")
public interface OneOpsEventResource {

    /**
     * Process OneOps event.
     *
     * @param event event's data
     * @return
     */
    @POST
    @Path("/oneops")
    @Consumes(MediaType.APPLICATION_JSON)
    Response event(Map<String, Object> event);

    /**
     * Process OneOps event.
     *
     * @param in event's data
     * @return
     */
    @POST
    @Path("/oneops")
    @Consumes(MediaType.WILDCARD)
    Response event(InputStream in);
}
