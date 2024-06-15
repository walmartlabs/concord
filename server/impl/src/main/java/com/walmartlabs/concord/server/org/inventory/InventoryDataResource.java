package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreAccessManager;
import com.walmartlabs.concord.server.org.jsonstore.JsonStoreEntry;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/org")
@Deprecated
@Tag(name = "Inventory Data")
public class InventoryDataResource implements Resource {

    private final OrganizationManager orgManager;
    private final JsonStoreAccessManager inventoryManager;
    private final InventoryDataDao inventoryDataDao;

    @Inject
    public InventoryDataResource(OrganizationManager orgManager,
                                 JsonStoreAccessManager inventoryManager,
                                 InventoryDataDao inventoryDataDao) {
        this.orgManager = orgManager;
        this.inventoryManager = inventoryManager;
        this.inventoryDataDao = inventoryDataDao;
    }

    /**
     * Returns an existing inventory data.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      data item path
     * @return
     */
    @GET
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get inventory data", operationId = "getInventoryData")
    public Object get(@PathParam("orgName") String orgName,
                      @PathParam("inventoryName") String inventoryName,
                      @PathParam("itemPath") String itemPath,
                      @QueryParam("singleItem") @DefaultValue("false") boolean singleItem) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry inventory = inventoryManager.assertAccess(org.getId(), null, inventoryName, ResourceAccessLevel.READER, true);

        if (singleItem) {
            return inventoryDataDao.getSingleItem(inventory.id(), itemPath);
        } else {
            return build(inventory.id(), itemPath);
        }
    }

    /**
     * Returns all inventory data for inventory.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @GET
    @Path("/{orgName}/inventory/{inventoryName}/data")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List inventory data", operationId = "listInventoryData")
    public List<Map<String, Object>> list(@PathParam("orgName") String orgName,
                                          @PathParam("inventoryName") String inventoryName) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry inventory = inventoryManager.assertAccess(org.getId(), null, inventoryName, ResourceAccessLevel.READER, true);

        return inventoryDataDao.list(inventory.id());
    }

    /**
     * Modifies inventory data
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      inventory's data path
     * @param data          inventory's data
     * @return full inventory data by path
     */
    @POST
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Modify inventory data", operationId = "updateInventoryData")
    public Object data(@PathParam("orgName") String orgName,
                       @PathParam("inventoryName") String inventoryName,
                       @PathParam("itemPath") String itemPath,
                       Object data) {

        // we expect all top-level entries to be JSON objects
        if (!itemPath.contains("/") && !(data instanceof Map)) {
            throw new ValidationErrorsException("Top-level inventory entries must be JSON objects. Got: " + data.getClass());
        }

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry inventory = inventoryManager.assertAccess(org.getId(), null, inventoryName, ResourceAccessLevel.WRITER, true);

        inventoryDataDao.merge(inventory.id(), itemPath, data);
        return build(inventory.id(), itemPath);
    }

    /**
     * Deletes inventory data
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @param itemPath      inventory's data path
     * @return
     */
    @DELETE
    @Path("/{orgName}/inventory/{inventoryName}/data/{itemPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete inventory data", operationId = "deleteInventoryData")
    public DeleteInventoryDataResponse delete(@PathParam("orgName") String orgName,
                                              @PathParam("inventoryName") String inventoryName,
                                              @PathParam("itemPath") String itemPath) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry inventory = inventoryManager.assertAccess(org.getId(), null, inventoryName, ResourceAccessLevel.WRITER, true);

        inventoryDataDao.delete(inventory.id(), itemPath);

        return new DeleteInventoryDataResponse();
    }

    private Object build(UUID inventoryId, String itemPath) {
        try {
            return JsonBuilder.build(inventoryDataDao.get(inventoryId, itemPath));
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while building the response: " + e.getMessage(), e);
        }
    }
}