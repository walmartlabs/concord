package com.walmartlabs.concord.server.api.user;

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

import com.walmartlabs.concord.common.validation.ConcordUsername;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Api(value = "Users", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/user")
public interface UserResource {

    /**
     * Creates a new user or updated an existing one.
     *
     * @param request user's data
     * @return
     */
    @POST
    @ApiOperation("Create a new user or update an existing one")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateUserResponse createOrUpdate(@ApiParam @Valid CreateUserRequest request);

    /**
     * Finds an existing user by username.
     *
     * @param username
     * @return
     */
    @GET
    @ApiOperation("Find a user")
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    UserEntry findByUsername(@PathParam("username") @ConcordUsername @NotNull String username);

    /**
     * Removes an existing user.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete an existing user")
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteUserResponse delete(@ApiParam @PathParam("id") UUID id);
}
