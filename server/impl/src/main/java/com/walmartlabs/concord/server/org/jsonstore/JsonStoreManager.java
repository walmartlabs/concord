package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.JsonStoreRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;

import javax.inject.Inject;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class JsonStoreManager {

    private static final String DEFAULT_POLICY_MESSAGE = "Maximum number of JSON stores exceeded: current {0}, limit {1}";

    private final PolicyManager policyManager;
    private final JsonStoreAccessManager jsonStoreAccessManager;
    private final OrganizationManager orgManager;
    private final UserManager userManager;
    private final AuditLog auditLog;
    private final JsonStoreDao storeDao;
    private final JsonStoreDataDao storeDataDao;
    private final OrganizationDao orgDao;
    private final TeamDao teamDao;

    @Inject
    public JsonStoreManager(PolicyManager policyManager,
                            JsonStoreAccessManager jsonStoreAccessManager,
                            OrganizationManager orgManager,
                            UserManager userManager,
                            AuditLog auditLog,
                            JsonStoreDao storeDao,
                            JsonStoreDataDao storeDataDao,
                            OrganizationDao orgDao,
                            TeamDao teamDao) {

        this.policyManager = policyManager;
        this.jsonStoreAccessManager = jsonStoreAccessManager;
        this.orgManager = orgManager;
        this.userManager = userManager;
        this.auditLog = auditLog;
        this.storeDao = storeDao;
        this.storeDataDao = storeDataDao;
        this.orgDao = orgDao;
        this.teamDao = teamDao;
    }

    public List<JsonStoreEntry> list(String orgName, int offset, int limit, String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            // admins or "global readers" can see any stores, so we shouldn't filter stores by user
            userId = null;
        }

        return storeDao.list(org.getId(), userId, offset, limit, filter);
    }

    public JsonStoreEntry get(String orgName, String storeName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
    }

    public JsonStoreCapacity getCapacity(String orgName, String storeName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);

        long currentSize = storeDataDao.getSize(store.id());

        PolicyEngine policy = policyManager.get(store.id(), null, UserPrincipal.assertCurrent().getId());
        Long maxSize = null;
        if (policy != null) {
            maxSize = policy.getJsonStoragePolicy().getMaxSize();
        }

        return JsonStoreCapacity.builder()
                .size(currentSize)
                .maxSize(maxSize)
                .build();
    }

    public void delete(String orgName, String storeName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.OWNER, true);

        storeDao.delete(store.id());

        addAuditLog(AuditAction.DELETE, org.getId(), store.id(), store.name());
    }

    public OperationResult createOrUpdate(String orgName, JsonStoreRequest entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID storeId = entry.id();
        String storeName = entry.name();

        if (storeId == null && storeName != null) {
            storeId = storeDao.getId(org.getId(), storeName);
        }

        if (storeId != null) {
            update(orgName, storeId, entry);
            return OperationResult.UPDATED;
        } else {
            JsonStoreVisibility visibility = entry.visibility();
            if (visibility == null) {
                visibility = JsonStoreVisibility.PRIVATE;
            }

            insert(orgName, entry.name(), visibility, entry.owner());
            return OperationResult.CREATED;
        }
    }

    public UUID insert(String orgName, String storeName, JsonStoreVisibility visibility, EntityOwner entityOwner) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UserEntry owner = getOwner(entityOwner, UserPrincipal.assertCurrent().getUser());

        policyManager.checkEntity(org.getId(), null, EntityType.JSON_STORE, EntityAction.CREATE, owner, PolicyUtils.jsonStoreToMap(org.getId(), storeName, visibility, owner));

        assertStoragePolicy(org.getId());

        UUID id = storeDao.insert(org.getId(), storeName, visibility, owner.getId());

        addAuditLog(AuditAction.CREATE, org.getId(), id, storeName);

        return id;
    }

    public void update(String orgName, UUID storeId, JsonStoreRequest entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        JsonStoreEntry prevStorage = storeDao.get(storeId);
        String prevStoreName = prevStorage.name();

        UserEntry owner = getOwner(entry.owner(), null);
        policyManager.checkEntity(org.getId(), null, EntityType.JSON_STORE, EntityAction.UPDATE, owner, PolicyUtils.jsonStoreToMap(org.getId(), prevStoreName, entry.visibility(), owner));

        UUID currentOwnerId = entry.owner() != null ? entry.owner().id() : null;
        UUID updatedOwnerId = owner != null ? owner.getId() : null;

        ResourceAccessLevel level = ResourceAccessLevel.WRITER;
        if (updatedOwnerId != null && !updatedOwnerId.equals(currentOwnerId)) {
            level = ResourceAccessLevel.OWNER;
        }

        prevStorage = jsonStoreAccessManager.assertAccess(org.getId(), null, prevStoreName, level, true);
        if (prevStorage == null) {
            throw new ValidationErrorsException("Can't find a JSON store '" + prevStoreName + "' in organization '" + org.getName() + "'");
        }

        UUID orgIdToUpdate = null;
        if (entry.orgName() != null) {
            OrganizationEntry organizationEntry = orgManager.assertAccess(entry.orgName(), true);
            if (organizationEntry.getId() != prevStorage.orgId()) {
                orgIdToUpdate = organizationEntry.getId();
            }
        }

        if (orgIdToUpdate != null) {
            assertStoragePolicy(orgIdToUpdate);
        }

        storeDao.update(prevStorage.id(), entry.name(), entry.visibility(), orgIdToUpdate, updatedOwnerId);

        JsonStoreEntry newStorage = storeDao.get(prevStorage.id());
        addAuditLog(AuditAction.UPDATE, prevStorage.orgId(), prevStorage.id(), prevStoreName, prevStorage, newStorage);
    }

    public List<ResourceAccessEntry> getResourceAccess(String orgName, String storeName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);

        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, false);
        return storeDao.getAccessLevel(store.id());
    }

    public void updateAccessLevel(String orgName, String storeName, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.OWNER, true);

        storeDao.tx(tx -> {
            if (isReplace) {
                storeDao.deleteTeamAccess(tx, store.id());
            }

            for (ResourceAccessEntry e : entries) {
                storeDao.upsertAccessLevel(tx, store.id(), e.getTeamId(), e.getLevel());
            }
        });

        addAuditLog(store.id(), entries, isReplace);
    }

    public void updateAccessLevel(String orgName, String storeName, ResourceAccessEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.OWNER, true);

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);
        storeDao.upsertAccessLevel(store.id(), teamId, entry.getLevel());

        addAuditLog(store.id(), Collections.singleton(new ResourceAccessEntry(teamId, null, null, entry.getLevel())), false);
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
            return userManager.get(owner.username(), owner.userDomain(), UserType.LDAP)
                    .orElseThrow(() -> new ConcordApplicationException("User not found: " + owner.username()));
        }

        return defaultOwner;
    }

    private void assertStoragePolicy(UUID orgId) {
        PolicyEngine policy = policyManager.get(orgId, null, UserPrincipal.assertCurrent().getUser().getId());
        if (policy == null) {
            return;
        }

        CheckResult<JsonStoreRule.StoreRule, Integer> result;
        try {
            result = policy.getJsonStoragePolicy().checkStorage(() -> storeDao.count(orgId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!result.getDeny().isEmpty()) {
            throw new ConcordApplicationException("Found JSON store policy violations: " + buildErrorMessage(result.getDeny()));
        }
    }

    private String buildErrorMessage(List<CheckResult.Item<JsonStoreRule.StoreRule, Integer>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<JsonStoreRule.StoreRule, Integer> e : errors) {
            JsonStoreRule.StoreRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            int actualCount = e.getEntity();
            int max = r.maxNumberPerOrg();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actualCount, max)).append(';');
        }
        return sb.toString();
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, UUID id, String name) {
        addAuditLog(auditAction, orgId, id, name, null, null);
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, UUID id, String name, Object prevEntity, Object newEntity) {
        auditLog.add(AuditObject.JSON_STORE, auditAction)
                .changes(prevEntity, newEntity)
                .field("orgId", orgId)
                .field("jsonStoreId", id)
                .field("name", name)
                .log();
    }

    private void addAuditLog(UUID storeId, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        List<ImmutableMap<String, ? extends Serializable>> teams = entries.stream()
                .map(e -> ImmutableMap.of("id", e.getTeamId(), "level", e.getLevel()))
                .collect(Collectors.toList());

        auditLog.add(AuditObject.JSON_STORE, AuditAction.UPDATE)
                .field("storeId", storeId)
                .field("access", ImmutableMap.of(
                        "replace", isReplace,
                        "teams", teams))
                .log();
    }

}
