package com.walmartlabs.concord.server.org.inventory;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.ResourceAccessUtils;
import com.walmartlabs.concord.server.sdk.audit.AuditObject;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Roles;
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
    private final OrganizationManager orgManager;
    private final AuditLog auditLog;

    @Inject
    public InventoryManager(InventoryDao inventoryDao,
                            UserDao userDao,
                            OrganizationManager orgManager,
                            AuditLog auditLog) {

        this.inventoryDao = inventoryDao;
        this.userDao = userDao;
        this.orgManager = orgManager;
        this.auditLog = auditLog;
    }

    public UUID insert(UUID orgId, InventoryEntry entry) {
        UUID parentId = assertParentInventoryAccess(orgId, entry.getParent(), ResourceAccessLevel.WRITER, true);

        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID ownerId = p.getId();

        UUID id = inventoryDao.insert(ownerId, entry.getName(), orgId, parentId, entry.getVisibility());

        auditLog.add(AuditObject.INVENTORY, AuditAction.CREATE)
                .field("id", id)
                .field("ordId", orgId)
                .field("name", entry.getName())
                .log();

        return id;
    }

    public void update(UUID inventoryId, InventoryEntry entry) {
        InventoryEntry prev = assertInventoryAccess(inventoryId, ResourceAccessLevel.WRITER, true);

        UUID parentId = assertParentInventoryAccess(prev.getOrgId(), entry.getParent(), ResourceAccessLevel.WRITER, true);

        InventoryVisibility visibility = entry.getVisibility();
        if (visibility == null) {
            visibility = InventoryVisibility.PUBLIC;
        }

        inventoryDao.update(inventoryId, entry.getName(), parentId, visibility);

        // TODO diff?
        auditLog.add(AuditObject.INVENTORY, AuditAction.UPDATE)
                .field("id", inventoryId)
                .log();
    }

    public void delete(UUID inventoryId) {
        InventoryEntry e = assertInventoryAccess(inventoryId, ResourceAccessLevel.WRITER, true);

        inventoryDao.delete(inventoryId);

        auditLog.add(AuditObject.INVENTORY, AuditAction.DELETE)
                .field("id", e.getId())
                .field("orgId", e.getOrgId())
                .field("name", e.getName())
                .log();
    }

    public void updateAccessLevel(UUID inventoryId, UUID teamId, ResourceAccessLevel level) {
        assertInventoryAccess(inventoryId, ResourceAccessLevel.OWNER, true);
        inventoryDao.upsertAccessLevel(inventoryId, teamId, level);
    }

    public InventoryEntry assertInventoryAccess(UUID orgId, String inventoryName, ResourceAccessLevel level, boolean orgMembersOnly) {
        UUID inventoryId = inventoryDao.getId(orgId, inventoryName);
        if (inventoryId == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryName);
        }

        return assertInventoryAccess(inventoryId, level, orgMembersOnly);
    }

    @WithTimer
    public InventoryEntry assertInventoryAccess(UUID inventoryId, ResourceAccessLevel level, boolean orgMembersOnly) {
        InventoryEntry e = inventoryDao.get(inventoryId);
        if (e == null) {
            throw new ValidationErrorsException("Inventory not found: " + inventoryId);
        }

        if (Roles.isAdmin()) {
            // an admin can access any project
            return e;
        }

        if (level == ResourceAccessLevel.READER && (Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            return e;
        } else if (level == ResourceAccessLevel.WRITER && Roles.isGlobalWriter()) {
            return e;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        InventoryOwner owner = e.getOwner();
        if (owner != null && owner.getId().equals(p.getId())) {
            // the owner can do anything with his inventories
            return e;
        }

        // TODO research the case for publicly writable inventories
        if (orgMembersOnly && e.getVisibility() == InventoryVisibility.PUBLIC
                && userDao.isInOrganization(p.getId(), e.getOrgId())) {
            // organization members can access any public inventory in the same organization
            return e;
        }

        OrganizationEntry org = orgManager.assertAccess(e.getOrgId(), false);
        if (ResourceAccessUtils.isSame(p, org.getOwner())) {
            // the org owner can do anything with the org's inventories
            return e;
        }

        if (orgMembersOnly || e.getVisibility() != InventoryVisibility.PUBLIC) {
            if (!inventoryDao.hasAccessLevel(inventoryId, p.getId(), ResourceAccessLevel.atLeast(level))) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't have " +
                        "the necessary access level (" + level + ") to the inventory: " + e.getName());
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
