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
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.jsonstore.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
@Singleton
@Api(value = "Inventories", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
@Deprecated
public class InventoryResource implements Resource {

    private final JsonStoreManager storageManager;
    private final JsonStoreDao storageDao;

    @Inject
    public InventoryResource(JsonStoreManager storageManager, JsonStoreDao storageDao) {
        this.storageManager = storageManager;
        this.storageDao = storageDao;
    }

    /**
     * List existing inventories.
     *
     * @param orgName organization's name
     * @return
     */
    @GET
    @ApiOperation(value = "List existing inventories", responseContainer = "list", response = InventoryEntry.class)
    @Path("/{orgName}/inventory")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InventoryEntry> list(@ApiParam @PathParam("orgName") String orgName) {
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
    @ApiOperation("Get existing inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    public InventoryEntry get(@ApiParam @PathParam("orgName") String orgName,
                              @ApiParam @PathParam("inventoryName") String inventoryName) {
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
    @ApiOperation("Create or update inventory")
    @Path("/{orgName}/inventory")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public CreateInventoryResponse createOrUpdate(@ApiParam @PathParam("orgName") String orgName,
                                                  @ApiParam @Valid InventoryEntry entry) {

        JsonStoreEntry prevStorage = null;
        if (entry.getId() != null) {
            prevStorage = storageDao.get(entry.getId());
        }

        if (prevStorage != null) {
            storageManager.update(orgName, prevStorage.id(), JsonStoreRequest.builder()
                    .name(entry.getName())
                    .owner(toOwner(entry.getOwner()))
                    .visibility(entry.getVisibility() != null ? entry.getVisibility() : JsonStoreVisibility.PRIVATE)
                    .build());

            return new CreateInventoryResponse(OperationResult.UPDATED, prevStorage.id());
        }

        UUID id = storageManager.insert(orgName, entry.getName(), entry.getVisibility() != null ? entry.getVisibility() : JsonStoreVisibility.PUBLIC, toOwner(entry.getOwner()));

        return new CreateInventoryResponse(OperationResult.CREATED, id);
    }

    @POST
    @ApiOperation("Updates the access level for the specified inventory")
    @Path("/{orgName}/inventory/{inventoryName}/access")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public GenericOperationResult updateAccessLevel(@ApiParam @PathParam("orgName") @ConcordKey String orgName,
                                                    @ApiParam @PathParam("inventoryName") @ConcordKey String inventoryName,
                                                    @ApiParam @Valid ResourceAccessEntry entry) {

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
    @ApiOperation("Delete inventory")
    @Path("/{orgName}/inventory/{inventoryName}")
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult delete(@ApiParam @PathParam("orgName") String orgName,
                                         @ApiParam @PathParam("inventoryName") String inventoryName) {

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
