package com.walmartlabs.concord.server.api.security.secret;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Api("Secrets")
@Path("/api/v1/secret")
@Deprecated
public interface SecretResource {

    @POST
    @ApiOperation("Create a new key pair")
    @Path("/keypair")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    UploadSecretResponse createOrUploadKeyPair(@ApiParam @QueryParam("name") @ConcordKey @NotNull String name,
                                               @ApiParam @QueryParam("generatePassword") @DefaultValue("false") boolean generatePassword,
                                               @ApiParam MultipartInput input);

    @POST
    @ApiOperation("Add a username and password secret")
    @Path("/password")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    UploadSecretResponse addUsernamePassword(@ApiParam @QueryParam("name") @ConcordKey @NotNull String name,
                                             @ApiParam @QueryParam("generatePassword") @DefaultValue("false") boolean generatePassword,
                                             @ApiParam MultipartInput input);

    @POST
    @ApiOperation("Add a plain value secret")
    @Path("/plain")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    UploadSecretResponse addPlainSecret(@ApiParam @QueryParam("name") @ConcordKey @NotNull String name,
                                        @ApiParam @QueryParam("generatePassword") @DefaultValue("false") boolean generatePassword,
                                        @ApiParam MultipartInput input);

    @POST
    @ApiOperation("Create a new key pair")
    @Path("/keypair")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    PublicKeyResponse createKeyPair(@ApiParam @QueryParam("name") @ConcordKey @NotNull String name,
                                    @ApiParam @QueryParam("teamId") UUID teamId,
                                    @ApiParam @QueryParam("teamName") String teamName);

    @POST
    @ApiOperation("Add a username and password secret")
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    UploadSecretResponse addUsernamePassword(@ApiParam @QueryParam("name") @ConcordKey @NotNull String name,
                                             @ApiParam @QueryParam("teamId") UUID teamId,
                                             @ApiParam @QueryParam("teamName") String teamName,
                                             @ApiParam @Valid UsernamePasswordRequest request);

    @GET
    @ApiOperation("Get an existing public key")
    @Path("/{secretName}/public")
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    PublicKeyResponse getPublicKey(@ApiParam @PathParam("secretName") @ConcordKey String secretName);

    @GET
    @ApiOperation("List secrets")
    @Produces(MediaType.APPLICATION_JSON)
    List<SecretEntry> list(@ApiParam @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                           @ApiParam @QueryParam("asc") @DefaultValue("true") boolean asc);

    @DELETE
    @ApiOperation("Delete an existing secret")
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteSecretResponse delete(@ApiParam @PathParam("name") @ConcordKey String name);
}
