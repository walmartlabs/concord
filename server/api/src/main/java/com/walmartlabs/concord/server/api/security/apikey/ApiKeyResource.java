package com.walmartlabs.concord.server.api.security.apikey;

import com.walmartlabs.concord.common.validation.ConcordId;

import javax.validation.Valid;
import javax.ws.rs.*;
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

    /**
     * Removes an API key.
     *
     * @param id
     * @return
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteApiKeyResponse delete(@PathParam("id") @ConcordId String id);
}
