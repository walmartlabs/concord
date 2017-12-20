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
        UUID parentId = assertParentInventoryAccess(orgId, entry.getParent(), ResourceAccessLevel.WRITER, true);

        UserPrincipal p = UserPrincipal.getCurrent();
        UUID ownerId = p.getId();

        return inventoryDao.insert(ownerId, entry.getName(), orgId, parentId, entry.getVisibility());
    }

    public void update(UUID inventoryId, InventoryEntry entry) {
        InventoryEntry prev = assertInventoryAccess(inventoryId, ResourceAccessLevel.WRITER, true);

        UUID parentId = assertParentInventoryAccess(prev.getOrgId(), entry.getParent(), ResourceAccessLevel.WRITER, true);

        InventoryVisibility visibility = entry.getVisibility();
        if (visibility == null) {
            visibility = InventoryVisibility.PUBLIC;
        }

        inventoryDao.update(inventoryId, entry.getName(), parentId, visibility);
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
        if (parent == null || (parent.getId() == null && parent.getName() == null)) {
            return null;
        }

        UUID parentId = parent.getId();
        if (parentId == null) {
            parentId = inventoryDao.getId(orgId, parent.getName());
            if (parentId == null) {
                throw new ValidationErrorsException("Parent inventory not found: " + parent.getName());
            }

            assertInventoryAccess(parentId, level, orgMembersOnly);
        }

        return parentId;
    }
}
