package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.policyengine.ExternalIdentityDelegationRule;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.user.ExternalIdentityDao;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Selects delegation ceilings from Concord's administrator-managed effective
 * policy, never workflow configuration. Does not establish an execution subject.
 */
public class ExternalIdentityDelegationManager {

    private final PolicyManager policyManager;
    private final ExternalIdentityDao identityDao;

    @Inject
    public ExternalIdentityDelegationManager(PolicyManager policyManager, ExternalIdentityDao identityDao) {
        this.policyManager = policyManager;
        this.identityDao = identityDao;
    }

    public ExternalIdentityDelegationPolicy requirePolicy(ExternalIdentityRef identity, UUID orgId,
                                                           UUID projectId, UUID repositoryId) {
        var binding = new ExternalIdentityDelegationPolicy.Binding(identity, orgId, projectId, repositoryId);
        var userId = identityDao.getUserId(identity)
                .orElseThrow(() -> new UnauthorizedException("External identity is not provisioned"));
        var engine = policyManager.get(orgId, projectId, userId);
        if (engine == null) {
            throw new UnauthorizedException("No effective policy allows external identity delegation");
        }
        var entry = engine.getExternalIdentityDelegationPolicy()
                .find(new ExternalIdentityDelegationRule.Identity(identity.providerId(), identity.authority(), identity.subjectId()),
                        orgId, projectId, repositoryId)
                .orElseThrow(() -> new UnauthorizedException("No external identity delegation rule matches the identity and integration"));
        return new ExternalIdentityDelegationPolicy(entry.id(), binding, ResourceAccessLevel.valueOf(entry.projectAccessLevel()),
                resources(entry.secrets()), resources(entry.jsonStores()));
    }

    private static Map<UUID, ResourceAccessLevel> resources(List<ExternalIdentityDelegationRule.Resource> resources) {
        return resources.stream().collect(Collectors.toUnmodifiableMap(ExternalIdentityDelegationRule.Resource::id,
                resource -> ResourceAccessLevel.valueOf(resource.accessLevel())));
    }
}
