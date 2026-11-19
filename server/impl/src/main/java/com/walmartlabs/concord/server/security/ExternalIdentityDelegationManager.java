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

import com.typesafe.config.Config;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Startup-loaded server policy. Does not read workflow configuration or payload
 * attributes and does not establish an execution subject.
 */
public class ExternalIdentityDelegationManager {

    private final Map<ExternalIdentityDelegationPolicy.Binding, ExternalIdentityDelegationPolicy> policies;

    @Inject
    public ExternalIdentityDelegationManager(Config cfg) {
        var indexed = new HashMap<ExternalIdentityDelegationPolicy.Binding, ExternalIdentityDelegationPolicy>();
        var ids = new HashSet<String>();
        for (var entry : cfg.getConfigList("externalIdentity.delegations")) {
            requireKeys(entry, Set.of("id", "identity", "orgId", "projectId", "repositoryId",
                    "projectAccessLevel", "secrets", "jsonStores"));
            var rawIdentity = entry.getConfig("identity");
            requireKeys(rawIdentity, Set.of("providerId", "authority", "subjectId"));
            var identity = new ExternalIdentityRef(rawIdentity.getString("providerId"),
                    rawIdentity.getString("authority"), rawIdentity.getString("subjectId"));
            if (identity.providerId().length() > 128 || identity.authority().length() > 512
                    || identity.subjectId().length() > 512) {
                throw new IllegalArgumentException("Delegation identity exceeds persistence limits: providerId=128, authority=512, subjectId=512");
            }
            var binding = new ExternalIdentityDelegationPolicy.Binding(identity, uuid(entry, "orgId"),
                    uuid(entry, "projectId"), uuid(entry, "repositoryId"));
            var policy = new ExternalIdentityDelegationPolicy(entry.getString("id"), binding,
                    ResourceAccessLevel.valueOf(entry.getString("projectAccessLevel")),
                    resources(entry, "secrets"), resources(entry, "jsonStores"));
            if (!ids.add(policy.id())) {
                throw new IllegalArgumentException("Duplicate external identity delegation policy ID: " + policy.id());
            }
            if (indexed.putIfAbsent(binding, policy) != null) {
                throw new IllegalArgumentException("Multiple external identity delegation policies match the same identity and integration");
            }
        }
        policies = Map.copyOf(indexed);
    }

    public ExternalIdentityDelegationPolicy requirePolicy(ExternalIdentityRef identity, UUID orgId,
                                                           UUID projectId, UUID repositoryId) {
        var binding = new ExternalIdentityDelegationPolicy.Binding(identity, orgId, projectId, repositoryId);
        var policy = policies.get(binding);
        if (policy == null) {
            throw new UnauthorizedException("No external identity delegation policy matches the identity and integration");
        }
        return policy;
    }

    private static Map<UUID, ResourceAccessLevel> resources(Config entry, String key) {
        var result = new HashMap<UUID, ResourceAccessLevel>();
        for (var resource : entry.getConfigList(key)) {
            requireKeys(resource, Set.of("id", "accessLevel"));
            var id = uuid(resource, "id");
            var level = ResourceAccessLevel.valueOf(resource.getString("accessLevel"));
            if (result.putIfAbsent(id, level) != null) {
                throw new IllegalArgumentException("Duplicate resource ID in delegation " + key + ": " + id);
            }
        }
        return Map.copyOf(result);
    }

    private static UUID uuid(Config cfg, String key) {
        var value = cfg.getString(key);
        var id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Delegation " + key + " must be a canonical UUID");
        }
        return id;
    }

    private static void requireKeys(Config cfg, Set<String> allowed) {
        for (var key : cfg.root().keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown external identity delegation configuration key: " + key);
            }
        }
    }
}
