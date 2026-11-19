package com.walmartlabs.concord.server.sdk.identity;

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
import java.util.Set;

/**
 * An authoritative identity lookup, not an incremental membership update.
 * Group identifiers must exactly match the canonical identifiers in Concord's
 * team mappings. They assert team membership, not Concord global roles.
 *
 * @param identity identity that was looked up; must equal the requested reference
 * @param status authoritative lifecycle state, never a transport or lookup error
 * @param displayName optional presentation name
 * @param email optional contact address, not an identity key
 * @param groups complete membership set; empty for inactive or missing identities
 */
public record ExternalIdentitySnapshot(ExternalIdentityRef identity,
                                       Status status,
                                       @Nullable String displayName,
                                       @Nullable String email,
                                       Set<String> groups) {

    public ExternalIdentitySnapshot {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(status, "status");
        groups = Set.copyOf(groups);
        groups.forEach(group -> ExternalIdentityRef.requireNonBlank(group, "group"));
        if (status != Status.ACTIVE && !groups.isEmpty()) {
            throw new IllegalArgumentException("Only active identities can have groups");
        }
    }

    public enum Status {
        /** The identity exists and is active, including when it has no groups. */
        ACTIVE,
        /** The identity exists but has been deactivated by its authority. */
        INACTIVE,
        /** The authority definitively reports that the identity does not exist. */
        NOT_FOUND
    }
}
