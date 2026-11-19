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

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ExternalIdentityDelegationPolicy {

    private final ExternalIdentityDelegationRule rule;

    public ExternalIdentityDelegationPolicy(@Nullable ExternalIdentityDelegationRule rule) {
        this.rule = rule;
    }

    public Optional<ExternalIdentityDelegationRule.Delegation> find(ExternalIdentityDelegationRule.Identity identity,
                                                                   UUID orgId, UUID projectId, UUID repositoryId) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        if (rule == null) {
            return Optional.empty();
        }
        return rule.allow().stream()
                .filter(entry -> entry.identity().equals(identity))
                .filter(entry -> entry.orgId().equals(orgId))
                .filter(entry -> entry.projectId().equals(projectId))
                .filter(entry -> entry.repositoryId().equals(repositoryId))
                .findFirst();
    }
}
