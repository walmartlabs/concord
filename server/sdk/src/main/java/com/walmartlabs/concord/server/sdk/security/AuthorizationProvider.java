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

import java.time.Duration;

/** An independently configured source of authorization observations. */
public interface AuthorizationProvider {
    String id();

    /** Configuration state, not a network health check. */
    boolean isActive();

    /** Pure namespace check; null is accepted only for account-UUID-based sources. */
    boolean acceptsIdentity(ExternalIdentityKey identity);

    Duration defaultMaxAge();

    default Duration defaultLoginMaxAge() {
        return defaultMaxAge();
    }

    /**
     * Returns an authoritative observation, including empty grants. Throws
     * AuthorizationSubjectNotFoundException for a missing subject and
     * AuthorizationUnavailableException for unavailable or invalid results.
     */
    AuthorizationData resolve(AuthorizationRequest request);
}
