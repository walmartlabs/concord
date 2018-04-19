package com.walmartlabs.concord.server.api.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.GenericOperationResult;
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(value = "Secrets", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
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
    @ApiOperation("Get an existing secret")
    @Path("/{orgName}/secret/{secretName}")
    @Produces(MediaType.APPLICATION_JSON)
    SecretEntry get(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                    @ApiParam @PathParam("secretName") @ConcordKey String secretName);

    @POST
    @ApiOperation("Retrieves a binary data secret using the provided password")
    @Path("/{orgName}/secret/{secretName}/data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response getData(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                     @ApiParam @PathParam("secretName") @ConcordKey String secretName,
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
    GenericOperationResult delete(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                  @ApiParam @PathParam("secretName") @ConcordKey String secretName);

    @POST
    @ApiOperation("Updates the access level for the specified secret and team")
    @Path("/{orgName}/secret/{secretName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                             @ApiParam @PathParam("secretName") @ConcordKey String secretName,
                                             @ApiParam @Valid ResourceAccessEntry entry);
}
