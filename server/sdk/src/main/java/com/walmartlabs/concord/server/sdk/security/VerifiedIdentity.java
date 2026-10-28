/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

package com.walmartlabs.concord.server.sdk.security;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Trusted authenticator output; attributes must be sanitized and contain no credentials or tokens. */
public record VerifiedIdentity(ExternalIdentityKey identity, UserProfile profile, Set<IdentityLookup> identityLookups,
                               Map<String, Object> authorizationAttributes, Instant attributesObservedAt, Instant attributesExpireAt) {
    public VerifiedIdentity {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(profile, "profile");
        identityLookups = Set.copyOf(identityLookups);
        authorizationAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(authorizationAttributes, "authorizationAttributes")));
        SecurityContractValidation.observation(attributesObservedAt, attributesExpireAt);
    }
}
