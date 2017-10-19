package com.walmartlabs.concord.server.inventory;

import com.walmartlabs.concord.server.api.inventory.DeleteInventoryDataResponse;
import com.walmartlabs.concord.server.api.inventory.InventoryDataResource;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.UUID;

@Named
public class InventoryDataResourceImpl implements InventoryDataResource, Resource {

    private final InventoryDao inventoryDao;
    private final InventoryDataDao inventoryDataDao;

    @Inject
    public InventoryDataResourceImpl(InventoryDao inventoryDao,
                                     InventoryDataDao inventoryDataDao) {
        this.inventoryDao = inventoryDao;
        this.inventoryDataDao = inventoryDataDao;
    }

    @Override
    public Object get(String inventoryName, String itemPath) throws IOException {
        UUID inventoryId = assertInventory(inventoryName);
        return JsonBuilder.build(inventoryDataDao.get(inventoryId, itemPath));
    }

    @Override
    public Object data(String inventoryName, String itemPath, Object data) throws IOException {
        UUID inventoryId = assertInventory(inventoryName);
        inventoryDataDao.merge(inventoryId, itemPath, data);
        return JsonBuilder.build(inventoryDataDao.get(inventoryId, itemPath));
    }

    @Override
    public DeleteInventoryDataResponse delete(String inventoryName, String itemPath) {
        UUID inventoryId = assertInventory(inventoryName);
        inventoryDataDao.delete(inventoryId, itemPath);
        return new DeleteInventoryDataResponse();
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
}
