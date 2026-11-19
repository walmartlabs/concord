package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit delegation ceilings. An absent rule or empty allow list denies
 * delegation. Entries never grant ordinary resource permissions.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExternalIdentityDelegationRule(List<Delegation> allow) implements Serializable {

    public ExternalIdentityDelegationRule {
        allow = List.copyOf(allow);
        var ids = new HashSet<String>();
        var bindings = new HashSet<List<?>>();
        for (var entry : allow) {
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate external identity delegation ID: " + entry.id());
            }
            if (!bindings.add(List.of(entry.identity(), entry.orgId(), entry.projectId(), entry.repositoryId()))) {
                throw new IllegalArgumentException("Multiple delegations match the same identity and integration");
            }
        }
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw unknownKey(key);
    }

    public record Delegation(String id, Identity identity, UUID orgId, UUID projectId, UUID repositoryId,
                             String projectAccessLevel, List<Resource> secrets, List<Resource> jsonStores) implements Serializable {

        public Delegation {
            requireText(id, "id", 128);
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(orgId, "orgId");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(repositoryId, "repositoryId");
            requireAccessLevel(projectAccessLevel);
            secrets = resources(secrets);
            jsonStores = resources(jsonStores);
        }

        @JsonAnySetter
        public void unknown(String key, Object value) {
            throw unknownKey(key);
        }
    }

    public record Identity(String providerId, String authority, String subjectId) implements Serializable {

        public Identity {
            requireText(providerId, "providerId", 128);
            requireText(authority, "authority", 512);
            requireText(subjectId, "subjectId", 512);
        }

        @JsonAnySetter
        public void unknown(String key, Object value) {
            throw unknownKey(key);
        }
    }

    public record Resource(UUID id, String accessLevel) implements Serializable {

        public Resource {
            Objects.requireNonNull(id, "id");
            requireAccessLevel(accessLevel);
        }

        @JsonAnySetter
        public void unknown(String key, Object value) {
            throw unknownKey(key);
        }
    }

    private static List<Resource> resources(List<Resource> resources) {
        var result = List.copyOf(resources);
        var ids = new HashSet<UUID>();
        for (var resource : result) {
            if (!ids.add(resource.id())) {
                throw new IllegalArgumentException("Duplicate delegation resource ID: " + resource.id());
            }
        }
        return result;
    }

    private static void requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Delegation " + name + " must contain between 1 and " + maxLength + " characters");
        }
    }

    private static void requireAccessLevel(String level) {
        Objects.requireNonNull(level, "accessLevel");
        if (!Set.of("READER", "WRITER", "OWNER").contains(level)) {
            throw new IllegalArgumentException("Invalid delegation access level: " + level);
        }
    }

    private static IllegalArgumentException unknownKey(String key) {
        return new IllegalArgumentException("Unknown external identity delegation rule key: " + key);
    }
}
