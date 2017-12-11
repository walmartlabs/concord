package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.inventory.CreateInventoryResponse;
import com.walmartlabs.concord.server.api.org.inventory.InventoryEntry;
import com.walmartlabs.concord.server.api.org.inventory.InventoryResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InventoryResourceImpl implements InventoryResource, Resource {

    private final InventoryManager inventoryManager;
    private final InventoryDao inventoryDao;
    private final OrganizationManager orgManager;

    @Inject
    public InventoryResourceImpl(InventoryManager inventoryManager, InventoryDao inventoryDao, OrganizationManager orgManager) {
        this.inventoryManager = inventoryManager;
        this.inventoryDao = inventoryDao;
        this.orgManager = orgManager;
    }

    @Override
    public InventoryEntry get(String orgName, String inventoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        UUID inventoryId = assertInventory(org.getId(), inventoryName);

        return inventoryManager.get(inventoryId);
    }

    @Override
    public CreateInventoryResponse createOrUpdate(String orgName, InventoryEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID inventoryId = inventoryDao.getId(org.getId(), entry.getName());

        if (inventoryId != null) {
            inventoryManager.update(inventoryId, entry);
            return new CreateInventoryResponse(OperationResult.UPDATED, inventoryId);
        } else {
            inventoryId = inventoryManager.insert(org.getId(), entry);
            return new CreateInventoryResponse(OperationResult.CREATED, inventoryId);
        }
    }

    @Override
    public GenericOperationResultResponse delete(String orgName, String inventoryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID inventoryId = assertInventory(org.getId(), inventoryName);

        inventoryManager.delete(inventoryId);

        return new GenericOperationResultResponse(OperationResult.DELETED);
    }

    private UUID assertInventory(UUID orgId, String inventoryName) {
        if (inventoryName == null) {
            throw new ValidationErrorsException("A valid inventory name is required");
        }

        UUID id = inventoryDao.getId(orgId, inventoryName);
        if (id == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryName);
        }
        return id;
    }
}
