package com.walmartlabs.concord.server.api.security.apikey;

import com.walmartlabs.concord.common.validation.ConcordId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Api("API key")
@Path("/api/v1/apikey")
public interface ApiKeyResource {

    /**
     * Creates a new API key.
     *
     * @param request
     * @return
     */
    @POST
    @ApiOperation("Create a new API key")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateApiKeyResponse create(@ApiParam @Valid CreateApiKeyRequest request);

    /**
     * Removes an API key.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing API key")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteApiKeyResponse delete(@ApiParam @PathParam("id") UUID id);
}
