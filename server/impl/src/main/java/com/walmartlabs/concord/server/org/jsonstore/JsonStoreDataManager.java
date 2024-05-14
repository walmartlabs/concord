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

import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.JsonStoreRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.ConcordObjectMapper;
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
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class JsonStoreDataManager {

    private static final String DEFAULT_POLICY_MESSAGE = "Maximum data size in the JSON store exceeded: current {0}, limit {1}";

    private final ConcordObjectMapper objectMapper;
    private final PolicyManager policyManager;
    private final OrganizationManager orgManager;
    private final JsonStoreAccessManager jsonStoreAccessManager;
    private final JsonStoreDataDao storeDataDao;
    private final AuditLog auditLog;

    @Inject
    public JsonStoreDataManager(ConcordObjectMapper objectMapper,
                                PolicyManager policyManager,
                                OrganizationManager orgManager,
                                JsonStoreAccessManager jsonStoreAccessManager,
                                JsonStoreDataDao storeDataDao,
                                AuditLog auditLog) {

        this.objectMapper = objectMapper;
        this.policyManager = policyManager;
        this.orgManager = orgManager;
        this.jsonStoreAccessManager = jsonStoreAccessManager;
        this.storeDataDao = storeDataDao;
        this.auditLog = auditLog;
    }

    public Object getItem(String orgName, String storeName, String itemPath) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return storeDataDao.get(store.id(), itemPath);
    }

    public List<String> listItems(String orgName, String storeName, int offset, int limit, String filter) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.READER, true);
        return storeDataDao.listPath(store.id(), offset, limit, filter);
    }

    public OperationResult createOrUpdate(String orgName, String storeName, String itemPath, Object data) {
        if (data == null) {
            throw new ValidationErrorsException("JSON Store entries cannot be null.");
        }

        // we expect all entries to be proper JSON objects
        if (!(data instanceof Map)) {
            throw new ValidationErrorsException("All JSON Store entries must be valid JSON objects. Got: " + data.getClass());
        }

        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.WRITER, true);

        String jsonData = objectMapper.toString(data);
        policyManager.checkEntity(org.getId(), null, EntityType.JSON_STORE_ITEM, EntityAction.UPDATE, null, PolicyUtils.jsonStoreItemToMap(org, store, itemPath, jsonData));

        Long currentItemSize = storeDataDao.getItemSize(store.id(), itemPath);
        assertStorageDataPolicy(org.getId(), store.id(), currentItemSize == null ? 0 : currentItemSize, jsonData);

        storeDataDao.upsert(store.id(), itemPath, jsonData);

        addAuditLog(currentItemSize != null ? AuditAction.UPDATE : AuditAction.CREATE, org.getId(), store.id(), itemPath);
        return currentItemSize != null ? OperationResult.UPDATED : OperationResult.CREATED;
    }

    public boolean delete(String orgName, String storeName, String itemPath) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        JsonStoreEntry store = jsonStoreAccessManager.assertAccess(org.getId(), null, storeName, ResourceAccessLevel.WRITER, true);

        boolean deleted = storeDataDao.delete(store.id(), itemPath);
        if (deleted) {
            addAuditLog(AuditAction.DELETE, org.getId(), store.id(), itemPath);
        }

        return deleted;
    }

    private void assertStorageDataPolicy(UUID orgId, UUID storeId, long currentItemSize, String jsonData) {
        PolicyEngine policy = policyManager.get(orgId, null, UserPrincipal.assertCurrent().getUser().getId());
        if (policy == null) {
            return;
        }

        CheckResult<JsonStoreRule.StoreDataRule, Long> result;
        try {
            result = policy.getJsonStoragePolicy().checkStorageData(() -> storeDataDao.getSize(storeId) - currentItemSize + jsonData.length());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!result.getDeny().isEmpty()) {
            throw new ConcordApplicationException("Found JSON store policy violations: " + buildErrorMessage(result.getDeny()));
        }
    }

    private static String buildErrorMessage(List<CheckResult.Item<JsonStoreRule.StoreDataRule, Long>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<JsonStoreRule.StoreDataRule, Long> e : errors) {
            JsonStoreRule.StoreDataRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            Long actual = e.getEntity();
            Long max = r.maxSizeInBytes();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actual, max)).append(';');
        }
        return sb.toString();
    }

    private void addAuditLog(AuditAction auditAction, UUID orgId, UUID storeId, String itemPath) {
        auditLog.add(AuditObject.JSON_STORE_DATA, auditAction)
                .field("orgId", orgId)
                .field("jsonStoreId", storeId)
                .field("itemPath", itemPath)
                .log();
    }
}
