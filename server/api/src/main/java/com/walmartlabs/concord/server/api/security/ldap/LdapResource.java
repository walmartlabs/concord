package com.walmartlabs.concord.server.api.security.ldap;

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

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Api(value = "LDAP", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/ldap")
public interface LdapResource {

    /**
     * Creates a new LDAP group to permissions mapping or updates an existing one.
     *
     * @param request
     * @return
     */
    @POST
    @ApiOperation("Create or update a LDAP group mapping")
    @Path("/mapping")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateLdapMappingResponse createOrUpdate(@ApiParam CreateLdapMappingRequest request);

    /**
     * Lists LDAP group mappings.
     *
     * @return
     */
    @GET
    @ApiOperation("List LDAP group mappings")
    @Path("/mapping")
    @Produces(MediaType.APPLICATION_JSON)
    List<LdapMappingEntry> listMappings();

    /**
     * Deletes an existing LDAP group mapping.
     *
     * @param id
     * @return
     */
    @DELETE
    @ApiOperation("Delete a LDAP group mapping")
    @Path("/mapping/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteLdapMappingResponse deleteMapping(@ApiParam @PathParam("id") @NotNull UUID id);

    /**
     * Lists user's groups.
     *
     * @param username
     * @return
     */
    @GET
    @ApiOperation("Get user's LDAP groups")
    @Path("/query/{username}/group")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> getLdapGroups(@ApiParam @PathParam("username") @ConcordUsername @NotNull String username);
}
