package com.walmartlabs.concord.server.api.security.secret;

import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Secrets")
@Path("/api/v1/secret")
public interface SecretResource {

    @POST
    @ApiOperation("Create a new key pair")
    @Path("/keypair")
    @Produces(MediaType.APPLICATION_JSON)
    PublicKeyResponse createKeyPair(@QueryParam("name") @ConcordKey @NotNull String name);

    @GET
    @ApiOperation("Get an existing public key")
    @Path("/{id}/public")
    @Produces(MediaType.APPLICATION_JSON)
    PublicKeyResponse getPublicKey(@PathParam("id") String id);

    @GET
    @ApiOperation("List secrets")
    @Produces(MediaType.APPLICATION_JSON)
    List<SecretEntry> list(@ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                           @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    @DELETE
    @ApiOperation("Delete an existing secret")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteSecretResponse delete(@PathParam("id") @ConcordId String id);
}
