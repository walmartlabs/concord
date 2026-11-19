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

import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An additional authorization ceiling, never a resource grant or proof of
 * lifecycle eligibility. Only trusted server configuration may supply it.
 */
public record ExternalIdentityDelegationPolicy(String id,
                                               Binding binding,
                                               ResourceAccessLevel projectAccessLevel,
                                               Map<UUID, ResourceAccessLevel> secrets,
                                               Map<UUID, ResourceAccessLevel> jsonStores) {

    public ExternalIdentityDelegationPolicy {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || id.length() > 128) {
            throw new IllegalArgumentException("Delegation policy ID must contain between 1 and 128 characters");
        }
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(projectAccessLevel, "projectAccessLevel");
        secrets = Map.copyOf(secrets);
        jsonStores = Map.copyOf(jsonStores);
    }

    public void assertProjectAccess(UUID projectId, ResourceAccessLevel level) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(level, "level");
        if (!binding.projectId().equals(projectId) || !allows(projectAccessLevel, level)) {
            throw new UnauthorizedException("External identity delegation does not allow " + level + " access to project " + projectId);
        }
    }

    public void assertSecretAccess(UUID secretId, ResourceAccessLevel level) {
        assertResourceAccess("secret", secrets, secretId, level);
    }

    public void assertJsonStoreAccess(UUID storeId, ResourceAccessLevel level) {
        assertResourceAccess("JSON store", jsonStores, storeId, level);
    }

    private static void assertResourceAccess(String type, Map<UUID, ResourceAccessLevel> resources,
                                             UUID resourceId, ResourceAccessLevel level) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(level, "level");
        if (!allows(resources.get(resourceId), level)) {
            throw new UnauthorizedException("External identity delegation does not allow " + level + " access to " + type + " " + resourceId);
        }
    }

    private static boolean allows(ResourceAccessLevel cap, ResourceAccessLevel requested) {
        return cap != null && switch (requested) {
            case READER -> true;
            case WRITER -> cap == ResourceAccessLevel.WRITER || cap == ResourceAccessLevel.OWNER;
            case OWNER -> cap == ResourceAccessLevel.OWNER;
        };
    }

    /**
     * The organization, project and repository IDs must come from Concord's
     * validated integration records, not event body fields or workflow arguments.
     */
    public record Binding(ExternalIdentityRef identity, UUID orgId, UUID projectId, UUID repositoryId) {

        public Binding {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(orgId, "orgId");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(repositoryId, "repositoryId");
        }
    }
}
