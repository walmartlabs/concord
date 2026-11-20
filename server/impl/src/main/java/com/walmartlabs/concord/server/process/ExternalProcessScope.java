package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-owned binding only. Resource grants and lifecycle eligibility must be
 * resolved again on use; neither workflow state nor a saved policy is authority.
 */
public record ExternalProcessScope(UUID userId, ExternalIdentityRef identity, UUID orgId,
                                   UUID projectId, UUID repositoryId) {

    public ExternalProcessScope {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(repositoryId, "repositoryId");
    }
}
