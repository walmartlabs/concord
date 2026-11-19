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

import java.util.Objects;

/**
 * Stable identity in a provider's namespace. All components are case-sensitive
 * opaque identifiers; callers must not derive them from mutable display names.
 *
 * @param providerId unique identifier of the registered identity provider
 * @param authority canonical identity-source instance, supplied by server configuration
 * @param subjectId immutable identifier within that authority
 */
public record ExternalIdentityRef(String providerId, String authority, String subjectId) {

    public ExternalIdentityRef {
        requireNonBlank(providerId, "providerId");
        requireNonBlank(authority, "authority");
        requireNonBlank(subjectId, "subjectId");
    }

    static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
