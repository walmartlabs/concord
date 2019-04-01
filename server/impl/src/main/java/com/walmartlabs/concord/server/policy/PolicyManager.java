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
import com.walmartlabs.concord.server.org.policy.PolicyRules;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
@Singleton
public class PolicyManager {

    private final PolicyDao policyDao;
    private final UserManager userManager;

    @Inject
    public PolicyManager(PolicyDao policyDao, UserManager userManager) {
        this.policyDao = policyDao;
        this.userManager = userManager;
    }

    public PolicyEngine get(UUID orgId, UUID projectId, UUID userId) {
        PolicyRules r = policyDao.getRules(orgId, projectId, userId);
        if (r == null) {
            return null;
        }

        return new PolicyEngine(r.rules());
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
            throw new ValidationErrorsException("Action forbidden: " + result.getDeny().get(0).getRule().getMsg());
        }
    }

    private Map<String, Object> getOwnerAttrs(UserEntry owner) {
        if (owner == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", owner.getId());
        attrs.put("username", owner.getName());
        attrs.put("userType", owner.getType().name());

        UserInfoProvider.UserInfo userInfo = userManager.getInfo(owner.getName(), owner.getType());
        if (userInfo == null) {
            throw new ValidationErrorsException("User not found: " + owner.getId());
        }

        attrs.put("email", userInfo.email());
        attrs.put("displayName", userInfo.displayName());
        if (userInfo.attributes() != null) {
            attrs.putAll(userInfo.attributes());
        }
        return attrs;
    }
}
