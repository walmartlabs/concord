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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.jsonstore.*;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/org")
@Deprecated
@Tag(name = "Inventories")
public class InventoryResource implements Resource {

    private final JsonStoreManager storageManager;
    private final JsonStoreDao storageDao;
    private final OrganizationManager orgManager;

    @Inject
    public InventoryResource(JsonStoreManager storageManager, JsonStoreDao storageDao, OrganizationManager orgManager) {
        this.storageManager = storageManager;
        this.storageDao = storageDao;
        this.orgManager = orgManager;
    }

    /**
     * List existing inventories.
     *
     * @param orgName organization's name
     * @return
     */
    @GET
    @Path("/{orgName}/inventory")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List existing inventories", operationId = "listInventories")
    public List<InventoryEntry> list(@PathParam("orgName") String orgName) {
        List<JsonStoreEntry> result = storageManager.list(orgName, -1, -1, null);
        return result.stream()
                .map(InventoryResource::convert)
                .collect(Collectors.toList());
    }

    /**
     * Returns an existing inventory.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return inventory
     */
    @GET
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get existing inventory", operationId = "getInventory")
    public InventoryEntry get(@PathParam("orgName") String orgName,
                              @PathParam("inventoryName") String inventoryName) {
        return convert(storageManager.get(orgName, inventoryName));
    }

    /**
     * Create or update a inventory.
     *
     * @param orgName organization's name
     * @param entry   inventory's data
     * @return
     */
    @POST
    @Path("/{orgName}/inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Create or update inventory", operationId = "createOrUpdateInventory")
    public CreateInventoryResponse createOrUpdate(@PathParam("orgName") String orgName,
                                                  @Valid InventoryEntry entry) {

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        OperationResult result = storageManager.createOrUpdate(orgName, JsonStoreRequest.builder()
                .id(entry.getId())
                .name(entry.getName())
                .owner(toOwner(entry.getOwner()))
                .visibility(entry.getVisibility() != null ? entry.getVisibility() : JsonStoreVisibility.PUBLIC)
                .build());

        UUID id = storageDao.getId(org.getId(), entry.getName());

        return new CreateInventoryResponse(result, id);
    }

    @POST
    @Path("/{orgName}/inventory/{inventoryName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Updates the access level for the specified inventory", operationId = "updateInventoryAccessLevel")
    public GenericOperationResult updateAccessLevel(@PathParam("orgName") @ConcordKey String orgName,
                                                    @PathParam("inventoryName") @ConcordKey String inventoryName,
                                                    @Valid ResourceAccessEntry entry) {

        storageManager.updateAccessLevel(orgName, inventoryName, entry);

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    /**
     * Creates a new inventory or updates an existing one.
     *
     * @param orgName       organization's name
     * @param inventoryName inventory's name
     * @return
     */
    @DELETE
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Delete inventory", operationId = "deleteInventory")
    public GenericOperationResult delete(@PathParam("orgName") String orgName,
                                         @PathParam("inventoryName") String inventoryName) {

        storageManager.delete(orgName, inventoryName);

        return new GenericOperationResult(OperationResult.DELETED);
    }

    private static InventoryEntry convert(JsonStoreEntry e) {
        if (e == null) {
            return null;
        }

        InventoryOwner owner = null;
        if (e.owner() != null) {
            owner = new InventoryOwner(e.owner().id(), e.owner().username());
        }

        return new InventoryEntry(e.id(), e.name(), e.orgId(), e.orgName(), e.visibility(), owner, null);
    }

    private static EntityOwner toOwner(InventoryOwner owner) {
        if (owner == null) {
            return null;
        }

        return EntityOwner.builder()
                .id(owner.getId())
                .username(owner.getUsername())
                .build();
    }
}
