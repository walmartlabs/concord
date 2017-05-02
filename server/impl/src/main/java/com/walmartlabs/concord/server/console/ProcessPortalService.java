package com.walmartlabs.concord.server.console;

import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/api/service/process_portal")
public interface ProcessPortalService {

    @GET
    @Path("/start")
    Response startProcess(@QueryParam("entryPoint") @NotNull String entryPoint);
}
