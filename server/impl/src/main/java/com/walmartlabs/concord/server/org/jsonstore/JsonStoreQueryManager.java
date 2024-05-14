package com.walmartlabs.concord.server.org.jsonstore;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import javax.inject.Inject;
import java.util.*;

public class JsonStoreQueryManager {

    private final PolicyManager policyManager;
    private final OrganizationManager orgManager;
    private final JsonStoreAccessManager jsonStoreAccessManager;
    private final JsonStoreQueryDao queryDao;
    private final JsonStoreQueryExecDao execDao;
    private final AuditLog auditLog;

    @Inject
    public JsonStoreQueryManager(PolicyManager policyManager,
                                 OrganizationManager orgManager,
                                 JsonStoreAccessManager jsonStoreAccessManager,
                                 JsonStoreQueryDao queryDao,
                                 JsonStoreQueryExecDao execDao,
                                 AuditLog auditLog) {

        this.policyManager = policyManager;
        this.orgManager = orgManager;
        this.jsonStoreAccessManager = jsonStoreAccessManager;
        this.queryDao = queryDao;
        this.execDao = execDao;
        this.auditLog = auditLog;
    }

    public JsonStoreQueryEntry get(String orgName, String storeName, String queryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return queryDao.get(store.id(), queryName);
    }

    public List<JsonStoreQueryEntry> list(String orgName, String storeName, int offset, int limit, String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return queryDao.list(store.id(), offset, limit, filter);
    }

    public OperationResult createOrUpdate(String orgName, String storeName, JsonStoreQueryRequest entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);

        UUID queryId = entry.id();
        String queryName = entry.name();

        String text = entry.text();
        validateQuery(text);

        if (queryId == null && queryName != null) {
            queryId = queryDao.getId(store.id(), queryName);
        }

        if (queryId == null) {
            policyManager.checkEntity(org.getId(), null, EntityType.JSON_STORE_QUERY, EntityAction.CREATE, null, PolicyUtils.jsonStoreQueryToMap(org, store, queryName, text));
            queryDao.insert(store.id(), queryName, text);
            addAuditLog(AuditAction.CREATE, org.getId(), store.id(), queryName, null, text);
            return OperationResult.CREATED;
        } else {
            policyManager.checkEntity(org.getId(), null, EntityType.JSON_STORE_QUERY, EntityAction.UPDATE, null, PolicyUtils.jsonStoreQueryToMap(org, store, queryName, text));

            JsonStoreQueryEntry prevEntry = queryDao.get(queryId);

            String prevText = prevEntry.text();
            if (Objects.equals(text, prevText)) {
                // no changes
                return OperationResult.ALREADY_EXISTS;
            }

            queryDao.update(queryId, text);
            addAuditLog(AuditAction.UPDATE, org.getId(), store.id(), queryName, prevEntry.text(), text);

            return OperationResult.UPDATED;
        }
    }

    public void delete(String orgName, String storeName, String queryName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);

        UUID id = queryDao.getId(store.id(), queryName);
        if (id == null) {
            throw new ValidationErrorsException("Query not found: " + queryName);
        }

        queryDao.delete(store.id(), queryName);

        addAuditLog(AuditAction.DELETE, org.getId(), store.id(), queryName);
    }

    public List<Object> exec(String orgName, String storeName, String queryName, Map<String, Object> params) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return execDao.exec(store.id(), queryName, params);
    }

    public List<Object> exec(String orgName, String storeName, String text, int maxLimit) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return execDao.execSql(store.id(), text, null, maxLimit);
    }

    private static void validateQuery(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationErrorsException("Query should not be empty");
        }
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, UUID storeId, String queryName) {
        addAuditLog(auditAction, orgId, storeId, queryName, null, null);
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, UUID storeId, String queryName, String prevQuery, String newQuery) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("prevQuery", prevQuery);
        changes.put("newQuery", newQuery);
        auditLog.add(AuditObject.JSON_STORE_QUERY, auditAction)
                .field("orgId", orgId)
                .field("jsonStoreId", storeId)
                .field("queryName", queryName)
                .field("changes", changes)
                .log();
    }
}
