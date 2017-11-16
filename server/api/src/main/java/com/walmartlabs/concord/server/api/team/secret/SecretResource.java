package com.walmartlabs.concord.server.api.team.secret;

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api("Secrets")
public interface SecretResource {

    @POST
    @ApiOperation("Creates a new secret")
    @Path("/{teamName}/secret")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    SecretOperationResponse create(@ApiParam @ConcordKey String teamName,
                                   @ApiParam MultipartInput input);
}
