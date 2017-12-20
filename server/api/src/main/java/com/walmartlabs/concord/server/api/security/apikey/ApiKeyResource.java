package com.walmartlabs.concord.server.api.security.apikey;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Api(value = "API key", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
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
