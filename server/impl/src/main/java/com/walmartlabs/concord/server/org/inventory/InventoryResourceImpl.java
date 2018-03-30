package com.walmartlabs.concord.server.org.inventory;

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

import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.inventory.CreateInventoryResponse;
import com.walmartlabs.concord.server.api.org.inventory.InventoryEntry;
import com.walmartlabs.concord.server.api.org.inventory.InventoryResource;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessUtils;
import com.walmartlabs.concord.server.org.team.TeamDao;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Named
public class InventoryResourceImpl implements InventoryResource, Resource {

    private final InventoryManager inventoryManager;
    private final InventoryDao inventoryDao;
    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;

    @Inject
    public InventoryResourceImpl(InventoryManager inventoryManager,
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

    @Override
    public InventoryEntry get(String orgName, String inventoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return assertInventory(org.getId(), inventoryName, ResourceAccessLevel.READER, false);
    }

    @Override
    @Validate
    public CreateInventoryResponse createOrUpdate(String orgName, InventoryEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID inventoryId = entry.getId();
        if (inventoryId == null) {
            inventoryId = inventoryDao.getId(org.getId(), entry.getName());
        }

        if (inventoryId != null) {
            inventoryManager.update(inventoryId, entry);
            return new CreateInventoryResponse(OperationResult.UPDATED, inventoryId);
        }

        inventoryId = inventoryManager.insert(org.getId(), entry);
        return new CreateInventoryResponse(OperationResult.CREATED, inventoryId);
    }

    @Override
    @Validate
    public GenericOperationResultResponse updateAccessLevel(String orgName, String inventoryName, ResourceAccessEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID inventoryId = inventoryDao.getId(org.getId(), inventoryName);
        if (inventoryId == null) {
            throw new WebApplicationException("Inventory not found: " + inventoryName, Response.Status.NOT_FOUND);
        }

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);

        inventoryManager.updateAccessLevel(inventoryId, teamId, entry.getLevel());

        return new GenericOperationResultResponse(OperationResult.UPDATED);
    }

    @Override
    public GenericOperationResultResponse delete(String orgName, String inventoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        InventoryEntry i = assertInventory(org.getId(), inventoryName, ResourceAccessLevel.OWNER, false);

        inventoryManager.delete(i.getId());

        return new GenericOperationResultResponse(OperationResult.DELETED);
    }

    private InventoryEntry assertInventory(UUID orgId, String inventoryName, ResourceAccessLevel accessLevel, boolean orgMembersOnly) {
        if (inventoryName == null) {
            throw new ValidationErrorsException("A valid inventory name is required");
        }

        return inventoryManager.assertInventoryAccess(orgId, inventoryName, accessLevel, orgMembersOnly);
    }
}
