package com.walmartlabs.concord.server.policy;

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

import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.EntityRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import java.util.*;

public class PolicyManager {

    private final PolicyCache policyCache;
    private final PolicyDao policyDao;
    private final UserManager userManager;

    @Inject
    public PolicyManager(PolicyCache policyCache, PolicyDao policyDao, UserManager userManager) {
        this.policyCache = policyCache;
        this.policyDao = policyDao;
        this.userManager = userManager;
    }

    public void refresh() {
        policyCache.refresh();
    }

    public PolicyEngine get(UUID orgId, UUID projectId, UUID userId) {
        return policyCache.get(orgId, projectId, userId);
    }

    public PolicyEntry get(String policyName) {
        return policyDao.get(policyName);
    }

    public UUID getId(String policyName) {
        return policyDao.getId(policyName);
    }

    public List<PolicyEntry> list() {
        return policyDao.list();
    }

    public PolicyEntry getLinked(UUID orgId, UUID projectId, UUID userId) {
        return policyDao.getLinked(orgId, projectId, userId);
    }

    public UUID insert(String name, UUID parentId, Map<String, Object> rules) {
        return policyDao.insert(name, parentId, rules);
    }

    public void update(UUID id, String name, UUID parentId, Map<String, Object> rules) {
        policyDao.update(id, name, parentId, rules);
        policyCache.refresh();
    }

    public void delete(UUID id) {
        policyDao.delete(id);
        policyCache.refresh();
    }

    public void link(UUID policyId, UUID orgId, UUID projectId, UUID userId) {
        policyDao.link(policyId, orgId, projectId, userId);
        policyCache.refresh();
    }

    public void unlink(UUID policyId, UUID orgId, UUID projectId, UUID userId) {
        policyDao.unlink(policyId, orgId, projectId, userId);
        policyCache.refresh();
    }

    public void checkEntity(UUID orgId, UUID projectId,
                            EntityType entityType, EntityAction action,
                            UserEntry owner, Map<String, Object> entityAttrs) {

        PolicyEngine pe = get(orgId, projectId, UserPrincipal.assertCurrent().getId());
        if (pe == null) {
            return;
        }

        CheckResult<EntityRule, Map<String, Object>> result = pe.getEntityPolicy().check(entityType.id(), action.id(), () -> {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("owner", getOwnerAttrs(owner));
            attrs.put("entity", entityAttrs);
            return attrs;
        });

        if (!result.getDeny().isEmpty()) {
            throw new ValidationErrorsException("Action forbidden: " + result.getDeny().get(0).getRule().msg());
        }
    }

    public PolicyEngine getPolicyEngine(Payload payload) {
        return get(payload.getHeader(Payload.ORGANIZATION_ID), payload.getHeader(Payload.PROJECT_ID), payload.getHeader(Payload.INITIATOR_ID));
    }

    private Map<String, Object> getOwnerAttrs(UserEntry owner) {
        if (owner == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", owner.getId());
        attrs.put("username", owner.getName());
        attrs.put("userType", owner.getType().name());

        UserInfoProvider.UserInfo userInfo = userManager.getInfo(owner.getName(), owner.getDomain(), owner.getType());
        if (userInfo == null) {
            throw new ValidationErrorsException("User not found: " + owner.getId());
        }

        attrs.put("email", userInfo.email());
        attrs.put("displayName", userInfo.displayName());
        if (userInfo.attributes() != null) {
            attrs.putAll(Objects.requireNonNull(userInfo.attributes()));
        }

        return attrs;
    }
}
