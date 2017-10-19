package com.walmartlabs.concord.server.inventory;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.inventory.CreateInventoryResponse;
import com.walmartlabs.concord.server.api.inventory.DeleteInventoryResponse;
import com.walmartlabs.concord.server.api.inventory.InventoryEntry;
import com.walmartlabs.concord.server.api.inventory.InventoryResource;
import com.walmartlabs.concord.server.team.TeamDao;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InventoryResourceImpl implements InventoryResource, Resource {

    private final InventoryDao inventoryDao;
    private final TeamDao teamDao;

    @Inject
    public InventoryResourceImpl(InventoryDao inventoryDao, TeamDao teamDao) {
        this.inventoryDao = inventoryDao;
        this.teamDao = teamDao;
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

        UUID teamId = assertOptionalTeam(entry.getTeamId(), entry.getTeamName());
        UUID inventoryId = inventoryDao.getId(entry.getName());

        if (inventoryId != null) {
            inventoryDao.update(inventoryId, entry.getName(), teamId, parentId);
            return new CreateInventoryResponse(OperationResult.UPDATED, inventoryId);
        } else {
            inventoryId = inventoryDao.insert(entry.getName(), teamId, parentId);
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

    private UUID assertOptionalTeam(UUID teamId, String teamName) {
        if (teamId != null) {
            if (teamDao.get(teamId) == null) {
                throw new ValidationErrorsException("Team not found: " + teamId);
            }
        }

        if (teamId == null && teamName != null) {
            teamId = teamDao.getId(teamName);
            if (teamId == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }
        }

        return teamId;
    }
}
