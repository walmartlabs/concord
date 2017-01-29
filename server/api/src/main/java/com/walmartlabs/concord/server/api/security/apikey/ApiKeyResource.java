package com.walmartlabs.concord.server.api.security.apikey;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/api/v1/apikey")
public interface ApiKeyResource {

    /**
     * Creates a new API key.
     *
     * @param request
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateApiKeyResponse create(@Valid CreateApiKeyRequest request);
}
