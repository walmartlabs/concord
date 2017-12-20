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

import com.walmartlabs.concord.common.validation.ConcordKey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "Role", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/role")
public interface RoleResource {

    /**
     * Create a new role or update an existing one.
     *
     * @param entry
     * @return
     */
    @POST
    @ApiOperation("Create or update role")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateRoleResponse createOrUpdate(@ApiParam RoleEntry entry);

    /**
     * List roles.
     *
     * @return
     */
    @GET
    @ApiOperation("List roles")
    @Produces(MediaType.APPLICATION_JSON)
    List<RoleEntry> get();

    /**
     * Delete an existing role.
     *
     * @param name
     * @return
     */
    @DELETE
    @ApiOperation("Delete a role")
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteRoleResponse delete(@ApiParam @PathParam("name") @ConcordKey String name);
}
