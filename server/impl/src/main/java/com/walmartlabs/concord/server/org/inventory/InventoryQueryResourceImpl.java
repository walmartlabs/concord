package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.inventory.*;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Named
public class InventoryQueryResourceImpl implements InventoryQueryResource, Resource {

    private final OrganizationManager orgManager;
    private final InventoryManager inventoryManager;
    private final InventoryQueryDao inventoryQueryDao;
    private final InventoryQueryExecDao inventoryQueryExecDao;

    @Inject
    public InventoryQueryResourceImpl(OrganizationManager orgManager,
                                      InventoryManager inventoryManager,
                                      InventoryQueryDao inventoryQueryDao,
                                      InventoryQueryExecDao inventoryQueryExecDao) {
        this.inventoryManager = inventoryManager;
        this.orgManager = orgManager;
        this.inventoryQueryDao = inventoryQueryDao;
        this.inventoryQueryExecDao = inventoryQueryExecDao;
    }

    @Override
    public InventoryQueryEntry get(String orgName, String inventoryName, String queryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.READER, true);

        UUID queryId = assertQuery(inventory.getId(), queryName);
        return inventoryQueryDao.get(queryId);
    }

    @Override
    public CreateInventoryQueryResponse createOrUpdate(String orgName, String inventoryName, String queryName, String text) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.READER, true);

        UUID inventoryId = inventory.getId();
        UUID queryId = inventoryQueryDao.getId(inventoryId, queryName);

        if (queryId == null) {
            queryId = inventoryQueryDao.insert(inventoryId, queryName, text);
            return new CreateInventoryQueryResponse(OperationResult.CREATED, queryId);
        } else {
            inventoryQueryDao.update(queryId, inventoryId, queryName, text);
            return new CreateInventoryQueryResponse(OperationResult.UPDATED, queryId);
        }
    }

    @Override
    public DeleteInventoryQueryResponse delete(String orgName, String inventoryName, String queryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.READER, true);

        UUID inventoryId = inventory.getId();
        UUID queryId = assertQuery(inventoryId, queryName);
        inventoryQueryDao.delete(queryId);
        return new DeleteInventoryQueryResponse();
    }

    @Override
    public List<Object> exec(String orgName, String inventoryName, String queryName, Map<String, Object> params) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        InventoryEntry inventory = inventoryManager.assertInventoryAccess(org.getId(), inventoryName, ResourceAccessLevel.READER, true);

        UUID inventoryId = inventory.getId();
        UUID queryId = assertQuery(inventoryId, queryName);
        return inventoryQueryExecDao.exec(queryId, params);
    }

    private UUID assertQuery(UUID inventoryId, String queryName) {
        if (queryName == null) {
            throw new ValidationErrorsException("A valid query name is required");
        }

        UUID id = inventoryQueryDao.getId(inventoryId, queryName);
        if (id == null) {
            throw new ValidationErrorsException("Query nor found: " + queryName);
        }
        return id;
    }
}
