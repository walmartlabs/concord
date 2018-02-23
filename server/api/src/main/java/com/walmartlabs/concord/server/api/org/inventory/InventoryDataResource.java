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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Api(value = "Inventory", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public interface InventoryDataResource {

    /**
     * Returns an existing inventory data.
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param itemPath data item path
     * @return
     */
    @GET
    @ApiOperation("Get inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    Object get(@ApiParam @PathParam("orgName") String orgName,
               @ApiParam @PathParam("inventoryName") String inventoryName,
               @ApiParam @PathParam("itemPath") String itemPath);

    /**
     * Modifies inventory data
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param itemPath inventory's data path
     * @param data inventory's data
     * @return full inventory data by path
     */
    @POST
    @ApiOperation("Modify inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Object data(@ApiParam @PathParam("orgName") String orgName,
                @ApiParam @PathParam("inventoryName") String inventoryName,
                @ApiParam @PathParam("itemPath") String itemPath,
                @ApiParam Object data);

    /**
     * Deletes inventory data
     *
     * @param orgName organization's name
     * @param inventoryName inventory's name
     * @param itemPath inventory's data path
     * @return
     */
    @DELETE
    @ApiOperation("Delete inventory data")
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    DeleteInventoryDataResponse delete(@ApiParam @PathParam("orgName") String orgName,
                                       @ApiParam @PathParam("inventoryName") String inventoryName,
                                       @ApiParam @PathParam("itemPath") String itemPath);
}
