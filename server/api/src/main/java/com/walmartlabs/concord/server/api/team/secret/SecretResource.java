package com.walmartlabs.concord.server.api.team.secret;

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.security.secret.SecretEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api("Secrets")
@Path("/api/v1/team")
public interface SecretResource {

    @POST
    @ApiOperation("Creates a new secret")
    @Path("/{teamName}/secret")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    SecretOperationResponse create(@ApiParam @ConcordKey String teamName,
                                   @ApiParam MultipartInput input);

    @GET
    @ApiOperation("Retrieves the public key of a key pair")
    @Path("/{teamName}/secret/{secretName}/public")
    @Produces(MediaType.APPLICATION_JSON)
    PublicKeyResponse getPublicKey(@ApiParam @ConcordKey String teamName,
                                   @ApiParam @ConcordKey String secretName);

    @GET
    @ApiOperation("List secrets")
    @Path("/{teamName}/secret")
    @Produces(MediaType.APPLICATION_JSON)
    List<SecretEntry> list(@ApiParam @ConcordKey String teamName);
}
