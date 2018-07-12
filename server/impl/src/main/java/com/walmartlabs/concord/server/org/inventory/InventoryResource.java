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
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.team.TeamDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Named
@Singleton
@Api(value = "Inventories", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/org")
public class InventoryResource implements Resource {

    private final InventoryManager inventoryManager;
    private final InventoryDao inventoryDao;
    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;

    @Inject
    public InventoryResource(InventoryManager inventoryManager,
                             InventoryDao inventoryDao,
                             OrganizationManager orgManager,
                             OrganizationDao orgDao,
                             TeamDao teamDao) {

        this.inventoryManager = inventoryManager;
        this.inventoryDao = inventoryDao;
        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.teamDao = teamDao;
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
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return inventoryDao.list(org.getId());
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

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return assertInventory(org.getId(), inventoryName, ResourceAccessLevel.READER, false);
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

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID id = entry.getId();
        if (id == null) {
            id = inventoryDao.getId(org.getId(), entry.getName());
        }

        if (id != null) {
            inventoryManager.update(id, entry);
            return new CreateInventoryResponse(OperationResult.UPDATED, id);
        }

        id = inventoryManager.insert(org.getId(), entry);

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

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID inventoryId = inventoryDao.getId(org.getId(), inventoryName);
        if (inventoryId == null) {
            throw new WebApplicationException("Inventory not found: " + inventoryName, Response.Status.NOT_FOUND);
        }

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);

        inventoryManager.updateAccessLevel(inventoryId, teamId, entry.getLevel());
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

        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        InventoryEntry i = assertInventory(org.getId(), inventoryName, ResourceAccessLevel.OWNER, false);
        inventoryManager.delete(i.getId());

        return new GenericOperationResult(OperationResult.DELETED);
    }

    private InventoryEntry assertInventory(UUID orgId, String inventoryName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (inventoryName == null) {
            throw new ValidationErrorsException("A valid inventory name is required");
        }

        return inventoryManager.assertInventoryAccess(orgId, inventoryName, accessLevel, orgMembersOnly);
    }
}
