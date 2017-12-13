package com.walmartlabs.concord.server.org.inventory;

import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.inventory.InventoryEntry;
import com.walmartlabs.concord.server.api.org.inventory.InventoryOwner;
import com.walmartlabs.concord.server.api.org.inventory.InventoryVisibility;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class InventoryManager {

    private final InventoryDao inventoryDao;
    private final UserDao userDao;

    @Inject
    public InventoryManager(InventoryDao inventoryDao, UserDao userDao) {
        this.inventoryDao = inventoryDao;
        this.userDao = userDao;
    }

    public InventoryEntry get(UUID inventoryId) {
        return assertInventoryAccess(inventoryId, ResourceAccessLevel.READER, false);
    }

    public UUID insert(UUID orgId, InventoryEntry entry) {
        UUID parentId = assertParentInventoryAccess(orgId, entry, ResourceAccessLevel.WRITER, true);

        UserPrincipal p = UserPrincipal.getCurrent();
        UUID ownerId = p.getId();

        return inventoryDao.insert(ownerId, entry.getName(), orgId, parentId, entry.getVisibility());
    }

    public void update(UUID inventoryId, InventoryEntry entry) {
        InventoryEntry prev = assertInventoryAccess(inventoryId, ResourceAccessLevel.WRITER, true);

        UUID parentId = assertParentInventoryAccess(prev.getOrgId(), entry.getParent(), ResourceAccessLevel.WRITER, true);

        inventoryDao.update(inventoryId, entry.getName(), parentId, entry.getVisibility());
    }

    public void delete(UUID inventoryId) {
        assertInventoryAccess(inventoryId, ResourceAccessLevel.WRITER, true);

        inventoryDao.delete(inventoryId);
    }

    public InventoryEntry assertInventoryAccess(UUID orgId, String inventoryName, ResourceAccessLevel level, boolean orgMembersOnly) {
        UUID inventoryId = inventoryDao.getId(orgId, inventoryName);
        if (inventoryId == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryId);
        }

        return assertInventoryAccess(inventoryId, level, orgMembersOnly);
    }

    public InventoryEntry assertInventoryAccess(UUID inventoryId, ResourceAccessLevel level, boolean orgMembersOnly) {
        InventoryEntry e = inventoryDao.get(inventoryId);
        if (e == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryId);
        }

        UserPrincipal p = UserPrincipal.getCurrent();
        if (p.isAdmin()) {
            // an admin can access any project
            return e;
        }

        InventoryOwner owner = e.getOwner();
        if (owner != null && owner.getId().equals(p.getId())) {
            // the owner can do anything with his inventories
            return e;
        }

        if (orgMembersOnly && e.getVisibility() == InventoryVisibility.PUBLIC
                && userDao.isInOrganization(p.getId(), e.getOrgId())) {
            // organization members can access any public inventory in the same organization
            return e;
        }

        if (orgMembersOnly || e.getVisibility() != InventoryVisibility.PUBLIC) {
            if (!inventoryDao.hasAccessLevel(inventoryId, p.getId(), ResourceAccessLevel.atLeast(level))) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                        "the necessary access level (" + level + ") to the inveitory: " + e.getName());
            }
        }

        return e;
    }

    private UUID assertParentInventoryAccess(UUID orgId, InventoryEntry parent, ResourceAccessLevel level, boolean orgMembersOnly) {
        if (parent == null) {
            return null;
        }

        UUID parentId = parent.getId();
        if (parentId == null) {
            parentId = inventoryDao.getId(orgId, parent.getName());
            if (parentId == null) {
                throw new ValidationErrorsException("Parent inventory not found: " + parentId);
            }

            assertInventoryAccess(parentId, level, orgMembersOnly);
        }

        return parentId;
    }
}
