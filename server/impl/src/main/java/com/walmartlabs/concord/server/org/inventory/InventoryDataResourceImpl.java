package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.inventory.DeleteInventoryDataResponse;
import com.walmartlabs.concord.server.api.org.inventory.InventoryDataResource;
import com.walmartlabs.concord.server.api.org.inventory.InventoryEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

@Named
public class InventoryDataResourceImpl implements InventoryDataResource, Resource {

    private final OrganizationManager orgManager;
    private final InventoryManager inventoryManager;
    private final InventoryDao inventoryDao;
    private final InventoryDataDao inventoryDataDao;

    @Inject
    public InventoryDataResourceImpl(OrganizationManager orgManager,
                                     InventoryManager inventoryManager,
                                     InventoryDao inventoryDao,
                                     InventoryDataDao inventoryDataDao) {
        this.orgManager = orgManager;
        this.inventoryManager = inventoryManager;
        this.inventoryDao = inventoryDao;
        this.inventoryDataDao = inventoryDataDao;
    }

    @Override
    public Object get(String orgName, String inventoryName, String itemPath) throws IOException {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.READER, true);

        return JsonBuilder.build(inventoryDataDao.get(inventory.getId(), itemPath));
    }

    @Override
    public Object data(String orgName, String inventoryName, String itemPath, Object data) throws IOException {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.WRITER, true);

        inventoryDataDao.merge(inventory.getId(), itemPath, data);
        return JsonBuilder.build(inventoryDataDao.get(inventory.getId(), itemPath));
    }

    @Override
    public DeleteInventoryDataResponse delete(String orgName, String inventoryName, String itemPath) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.WRITER, true);

        inventoryDataDao.delete(inventory.getId(), itemPath);

        return new DeleteInventoryDataResponse();
    }
}
