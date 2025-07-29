package com.walmartlabs.concord.server.org;

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

import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.project.DiffUtils;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authz.AuthorizationException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

public class OrganizationManager {

    // as defined in com/walmartlabs/concord/server/db/0.48.0.xml
    public static final UUID DEFAULT_ORG_ID = UUID.fromString("0fac1b18-d179-11e7-b3e7-d7df4543ed4f");
    public static final String DEFAULT_ORG_NAME = "Default";

    private final PolicyManager policyManager;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;
    private final UserManager userManager;
    private final Locks locks;
    private final AuditLog auditLog;

    @Inject
    public OrganizationManager(PolicyManager policyManager,
                               OrganizationDao orgDao,
                               TeamDao teamDao,
                               UserManager userManager,
                               Locks locks,
                               AuditLog auditLog) {

        this.policyManager = policyManager;
        this.orgDao = orgDao;
        this.teamDao = teamDao;
        this.userManager = userManager;
        this.locks = locks;
        this.auditLog = auditLog;
    }

    /**
     * Creates a new organization or updates an existing one.
     * <p/>
     * To update an existing organization, a {@link OrganizationEntry#getId()}
     * value must be provided.
     * <p/>
     * When updating an existing organization, only non-null properties are updated.
     * E.g. if you wish to update only the org's visibility, set only the ID and the
     * visibility values.
     *
     * @apiNote the method uses DB advisory locks, it is thread-safe across the cluster.
     */
    public OrganizationOperationResult createOrUpdate(OrganizationEntry entry) {
        return orgDao.txResult(tx -> createOrUpdate(tx, entry));
    }

    /**
     * @see #createOrUpdate(OrganizationEntry)
     */
    public OrganizationOperationResult createOrUpdate(DSLContext tx, OrganizationEntry entry) {
        // use advisory locks to avoid races
        locks.lock(tx, "OrganizationManager#createOrX");

        UUID orgId = entry.getId();
        if (orgId == null) {
            orgId = orgDao.getId(tx, entry.getName());
        }

        if (orgId == null) {
            orgId = create(tx, entry);
            return OrganizationOperationResult.builder()
                    .orgId(orgId)
                    .result(OperationResult.CREATED)
                    .build();
        } else {
            update(tx, orgId, entry);
            return OrganizationOperationResult.builder()
                    .orgId(orgId)
                    .result(OperationResult.UPDATED)
                    .build();
        }
    }

    public OrganizationOperationResult createOrGet(String orgName) {
        return orgDao.txResult(tx -> createOrGet(tx, orgName));
    }

    public OrganizationOperationResult createOrGet(DSLContext tx, String orgName) {
        // use advisory locks to avoid races
        // TODO optimistic locking as a possible optimization
        locks.lock(tx, "OrganizationManager#createOrX");

        UUID orgId = orgDao.getId(tx, orgName);
        if (orgId != null) {
            return OrganizationOperationResult.builder()
                    .orgId(orgId)
                    .result(OperationResult.ALREADY_EXISTS)
                    .build();
        }

        orgId = create(tx, new OrganizationEntry(orgName));
        return OrganizationOperationResult.builder()
                .orgId(orgId)
                .result(OperationResult.CREATED)
                .build();
    }

    private UUID create(DSLContext tx, OrganizationEntry entry) {
        assertPermission(Permission.CREATE_ORG);

        UserEntry owner = getOwner(entry.getOwner(), UserPrincipal.assertCurrent().getUser());

        policyManager.checkEntity(null, null, EntityType.ORGANIZATION, EntityAction.CREATE, owner, PolicyUtils.orgToMap(entry));

        UUID orgId = orgDao.insert(tx, entry.getName(), owner.getId(), entry.getVisibility(), entry.getMeta(), entry.getCfg());

        // ...add the owner user into the default team of the new org
        UUID teamId = teamDao.insert(tx, orgId, TeamManager.DEFAULT_TEAM_NAME, "Default team");
        teamDao.upsertUser(tx, teamId, owner.getId(), TeamRole.OWNER);

        Map<String, Object> changes = DiffUtils.compare(null, entry);
        addAuditLog(AuditAction.CREATE,
                orgId,
                entry.getName(),
                changes);

        return orgId;
    }

    private void update(DSLContext tx, UUID orgId, OrganizationEntry entry) {
        OrganizationEntry prevEntry = assertUpdateAccess(orgId);

        UserEntry owner = getOwner(entry.getOwner(), null);

        policyManager.checkEntity(orgId, null, EntityType.ORGANIZATION, EntityAction.UPDATE, owner, PolicyUtils.orgToMap(entry));

        UUID ownerId = owner != null ? owner.getId() : null;
        orgDao.update(tx, orgId, entry.getName(), ownerId, entry.getVisibility(), entry.getMeta(), entry.getCfg());

        OrganizationEntry newEntry = orgDao.get(orgId);

        Map<String, Object> changes = DiffUtils.compare(prevEntry, newEntry);
        addAuditLog(
                AuditAction.UPDATE,
                prevEntry.getId(),
                prevEntry.getName(),
                changes);
    }

    public void delete(String orgName) {
        assertAdmin();

        OrganizationEntry org = assertExisting(null, orgName);

        orgDao.delete(org.getId());

        addAuditLog(
                AuditAction.DELETE,
                org.getId(),
                org.getName(),
                null);
    }

    public OrganizationEntry assertExisting(UUID orgId, String orgName) {
        return orgDao.txResult(tx -> assertExisting(tx, orgId, orgName));
    }

    public OrganizationEntry assertExisting(DSLContext tx, UUID orgId, String orgName) {
        if (orgId != null) {
            OrganizationEntry e = orgDao.get(tx, orgId);
            if (e == null) {
                throw new ValidationErrorsException("Organization not found: " + orgId);
            }
            return e;
        }

        if (orgName != null) {
            OrganizationEntry e = orgDao.getByName(tx, orgName);
            if (e == null) {
                throw new ValidationErrorsException("Organization not found: " + orgName);
            }
            return e;
        }

        throw new ValidationErrorsException("Organization ID or name is required");
    }

    public OrganizationEntry assertAccess(UUID orgId, boolean orgMembersOnly) {
        return assertAccess(orgId, null, orgMembersOnly);
    }

    public OrganizationEntry assertAccess(DSLContext tx, UUID orgId, boolean orgMembersOnly) {
        return assertAccess(tx, orgId, null, orgMembersOnly);
    }

    public OrganizationEntry assertAccess(String orgName, boolean orgMembersOnly) {
        return assertAccess(null, orgName, orgMembersOnly);
    }

    @WithTimer
    public OrganizationEntry assertAccess(UUID orgId, String orgName, boolean orgMembersOnly) {
        return orgDao.txResult(tx -> assertAccess(tx, orgId, orgName, orgMembersOnly));
    }

    @WithTimer
    public OrganizationEntry assertAccess(DSLContext tx, UUID orgId, String orgName, boolean orgMembersOnly) {
        OrganizationEntry e = assertExisting(tx, orgId, orgName);

        if (Roles.isAdmin()) {
            // an admin can access any organization
            return e;
        }

        if (Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            return e;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        EntityOwner owner = e.getOwner();
        if (ResourceAccessUtils.isSame(p, owner)) {
            // the owner can do anything with his organization
            return e;
        }

        if (orgMembersOnly) {
            if (!userManager.isInOrganization(tx, e.getId())) {
                throw new UnauthorizedException("The current user (" + p.getUsername() + ") doesn't belong to the specified organization: " + e.getName());
            }
        }

        return e;
    }

    private OrganizationEntry assertUpdateAccess(UUID orgId) {
        OrganizationEntry entry = assertExisting(orgId, null);

        UserEntry owner = getOwner(entry.getOwner(), null);
        UUID ownerId = owner != null ? owner.getId() : null;
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.getId().equals(ownerId)) {
            return entry;
        }

        assertPermission(Permission.UPDATE_ORG);

        return entry;
    }

    private static void assertAdmin() {
        if (!Roles.isAdmin()) {
            throw new AuthorizationException("Only admins are allowed to update organizations");
        }
    }

    private static void assertPermission(Permission p) {
        if (p.isPermitted()) {
            return;
        }

        throw new AuthorizationException(
                String.format("Only roles with '%s' permission are allowed to update organizations", p.getKey())
        );
    }

    private UserEntry getOwner(EntityOwner owner, UserEntry defaultOwner) {
        if (owner == null) {
            return defaultOwner;
        }

        if (owner.id() != null) {
            return userManager.get(owner.id())
                    .orElseThrow(() -> new ValidationErrorsException("User not found: " + owner.id()));
        }

        if (owner.username() != null) {
            UserType t = owner.userType() != null ? owner.userType() : UserPrincipal.assertCurrent().getType();
            return userManager.get(owner.username(), owner.userDomain(), t)
                    .orElseThrow(() -> new ConcordApplicationException("User not found: " + owner.username()));
        }

        return defaultOwner;
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, String orgName, Map<String, Object> changes) {
        auditLog.add(AuditObject.ORGANIZATION, auditAction)
                .field("orgId", orgId)
                .field("name", orgName)
                .field("changes", changes)
                .log();
    }
}
