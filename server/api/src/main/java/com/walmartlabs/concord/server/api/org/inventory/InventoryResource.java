package com.walmartlabs.concord.server.api.org.inventory;

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

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "Inventories", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface InventoryResource {

    /**
     * Returns an existing inventory.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return inventory
     */
    @GET
    @ApiOperation("Get existing inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    InventoryEntry get(@ApiParam @PathParam("orgName") String orgName,
                       @ApiParam @PathParam("inventoryName") String inventoryName);

    /**
     * Create or update a inventory.
     *
     * @param orgName organization's name
     * @param entry   inventory's data
     * @return
     */
    @POST
    @ApiOperation("Create or update inventory")
    @Path("/{orgName}/inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    CreateInventoryResponse createOrUpdate(@ApiParam @PathParam("orgName") String orgName,
                                           @ApiParam @Valid InventoryEntry entry);


    @POST
    @ApiOperation("Updates the access level for the specified inventory")
    @Path("/{orgName}/inventory/{inventoryName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                             @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                                             @ApiParam @Valid ResourceAccessEntry entry);

    /**
     * Creates a new inventory or updates an existing one.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    GenericOperationResult delete(@ApiParam @PathParam("orgName") String orgName,
                                  @ApiParam @PathParam("inventoryName") String inventoryName);
}
