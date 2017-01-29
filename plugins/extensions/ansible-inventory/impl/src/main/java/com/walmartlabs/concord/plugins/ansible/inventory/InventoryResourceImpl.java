package com.walmartlabs.concord.plugins.ansible.inventory;

import com.walmartlabs.concord.plugins.ansible.inventory.InventoryDao.InventoryRecord;
import com.walmartlabs.concord.plugins.ansible.inventory.api.*;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.*;

import static com.walmartlabs.concord.plugins.ansible.inventory.jooq.public_.tables.AnsibleInventories.ANSIBLE_INVENTORIES;

@Named
public class InventoryResourceImpl implements InventoryResource, Resource {

    private final InventoryDao inventoryDao;

    private final Map<String, Field<?>> key2Field;

    @Inject
    public InventoryResourceImpl(InventoryDao inventoryDao) {
        this.inventoryDao = inventoryDao;

        this.key2Field = new HashMap<>();
        key2Field.put("inventoryId", ANSIBLE_INVENTORIES.INVENTORY_ID);
        key2Field.put("name", ANSIBLE_INVENTORIES.INVENTORY_NAME);
    }

    @Override
    @Validate
    @RequiresPermissions(Permissions.INVENTORY_CREATE_NEW)
    public CreateInventoryResponse create(String name, InputStream data) {
        String id = UUID.randomUUID().toString();
        inventoryDao.insert(id, name, data);
        return new CreateInventoryResponse(id);
    }

    @Override
    @Validate
    public InventoryEntry get(String id) {
        InventoryRecord r = inventoryDao.get(id);
        if (r == null) {
            throw new WebApplicationException("Inventory not found: " + id, Status.NOT_FOUND);
        }

        SecurityCheckResult check = check(r);
        if (check == SecurityCheckResult.NOTHING) {
            throw new WebApplicationException("The current user does not have permissions to access this inventory file", Status.FORBIDDEN);
        }

        boolean readOnly = check != SecurityCheckResult.MODIFY;
        return new InventoryEntry(r.getId(), r.getName(), readOnly);
    }

    @Override
    @Validate
    public InputStream getData(String id) {
        InventoryEntry e = get(id);
        return inventoryDao.getData(e.getId());
    }

    @Override
    @Validate
    public UpdateInventoryResponse update(String id, InputStream data) {
        assertPermissions(id, Permissions.INVENTORY_MANAGE_INSTANCE,
                "The current user does not have permissions to update this inventory file");
        inventoryDao.update(id, data);
        return new UpdateInventoryResponse();
    }

    @Override
    @Validate
    public DeleteInventoryResponse delete(String id) {
        assertPermissions(id, Permissions.INVENTORY_MANAGE_INSTANCE,
                "The current user does not have permissions to delete this inventory file");
        inventoryDao.delete(id);
        return new DeleteInventoryResponse();
    }

    @Override
    public List<InventoryEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);

        List<InventoryEntry> result = new ArrayList<>();
        for (InventoryRecord r : inventoryDao.list(sortField, asc)) {
            SecurityCheckResult check = check(r);
            if (check == SecurityCheckResult.NOTHING) {
                continue;
            }

            boolean readOnly = check != SecurityCheckResult.MODIFY;
            result.add(new InventoryEntry(r.getId(), r.getName(), readOnly));
        }

        return result;
    }

    private InventoryRecord assertPermissions(String id, String wildcard, String message) {
        InventoryRecord r = inventoryDao.get(id);
        if (r == null) {
            throw new WebApplicationException("Inventory not found: " + id, Status.NOT_FOUND);
        }

        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(wildcard, r.getName()))) {
            throw new WebApplicationException(message, Status.FORBIDDEN);
        }

        return r;
    }

    private static SecurityCheckResult check(InventoryRecord r) {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isPermitted(String.format(Permissions.INVENTORY_MANAGE_INSTANCE, r.getName()))) {
            return SecurityCheckResult.MODIFY;
        }
        if (subject.isPermitted(String.format(Permissions.INVENTORY_USE_INSTANCE, r.getName()))) {
            return SecurityCheckResult.VIEW;
        }
        return SecurityCheckResult.NOTHING;
    }

    private enum SecurityCheckResult {
        NOTHING,
        VIEW,
        MODIFY
    }
}
