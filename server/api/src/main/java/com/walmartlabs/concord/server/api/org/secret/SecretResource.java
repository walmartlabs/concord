package com.walmartlabs.concord.server.api.org.secret;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Secrets")
@Path("/api/v1/org")
public interface SecretResource {

    @POST
    @ApiOperation("Creates a new secret")
    @Path("/{orgName}/secret")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    SecretOperationResponse create(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam MultipartInput input);

    @GET
    @ApiOperation("Retrieves the public key of a key pair")
    @Path("/{orgName}/secret/{secretName}/public")
    @Produces(MediaType.APPLICATION_JSON)
    PublicKeyResponse getPublicKey(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                   @ApiParam @PathParam("secretName") @ConcordKey String secretName);

    @GET
    @ApiOperation("List secrets")
    @Path("/{orgName}/secret")
    @Produces(MediaType.APPLICATION_JSON)
    List<SecretEntry> list(@ApiParam @PathParam("orgName") @ConcordKey String orgName);

    @DELETE
    @ApiOperation("Delete an existing secret")
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteSecretResponse delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                @ApiParam @PathParam("secretName") @ConcordKey String secretName);
}
