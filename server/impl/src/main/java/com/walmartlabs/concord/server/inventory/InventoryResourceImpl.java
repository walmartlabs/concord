package com.walmartlabs.concord.server.inventory;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.inventory.CreateInventoryResponse;
import com.walmartlabs.concord.server.api.inventory.DeleteInventoryResponse;
import com.walmartlabs.concord.server.api.inventory.InventoryEntry;
import com.walmartlabs.concord.server.api.inventory.InventoryResource;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InventoryResourceImpl implements InventoryResource, Resource {

    private final InventoryDao inventoryDao;
    private final OrganizationManager orgManager;

    @Inject
    public InventoryResourceImpl(InventoryDao inventoryDao, OrganizationManager orgManager) {
        this.inventoryDao = inventoryDao;
        this.orgManager = orgManager;
    }

    @Override
    public InventoryEntry get(String inventoryName) {
        UUID inventoryId = assertInventory(inventoryName);
        return inventoryDao.get(inventoryId);
    }

    @Override
    public CreateInventoryResponse createOrUpdate(InventoryEntry entry) {
        UUID parentId = null;
        if (entry.getParent() != null) {
           parentId = assertInventory(entry.getParent().getName());
        }

        OrganizationEntry org = assertOrganization(entry.getOrgId(), entry.getOrgName());
        UUID inventoryId = inventoryDao.getId(entry.getName());

        if (inventoryId != null) {
            inventoryDao.update(inventoryId, entry.getName(), org.getId(), parentId);
            return new CreateInventoryResponse(OperationResult.UPDATED, inventoryId);
        } else {
            inventoryId = inventoryDao.insert(entry.getName(), org.getId(), parentId);
            return new CreateInventoryResponse(OperationResult.CREATED, inventoryId);
        }
    }

    @Override
    public DeleteInventoryResponse delete(String inventoryName) {
        UUID inventoryId = assertInventory(inventoryName);
        inventoryDao.delete(inventoryId);
        return new DeleteInventoryResponse();
    }

    private UUID assertInventory(String inventoryName) {
        if (inventoryName == null) {
            throw new ValidationErrorsException("A valid inventory name is required");
        }

        UUID id = inventoryDao.getId(inventoryName);
        if (id == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryName);
        }
        return id;
    }

    private OrganizationEntry assertOrganization(UUID orgId, String orgName) {
        if (orgId == null && orgName == null) {
            // TODO teams
            orgId = OrganizationManager.DEFAULT_ORG_ID;
        }

        return orgManager.assertAccess(orgId, orgName, true);
    }
}
