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

/**
 * Supplies authoritative identity state. Implementations must be thread-safe.
 * Concord, not the provider, owns user provisioning, membership persistence,
 * freshness policy and authorization.
 */
public interface ExternalIdentityProvider {

    /**
     * Stable, nonblank provider identifier. Duplicate registrations are invalid.
     */
    String getId();

    /**
     * Looks up an identity belonging to this provider and a configured authority.
     * Returns a non-null snapshot for exactly the requested identity, with the
     * complete membership set. Partial or paginated results must not be returned
     * as a complete snapshot.
     *
     * @throws ExternalIdentityException if lookup cannot be completed, including
     *         unavailable authorities, malformed responses and incomplete results;
     *         such failures must not be converted to an empty or missing identity
     */
    ExternalIdentitySnapshot fetch(ExternalIdentityRef identity) throws ExternalIdentityException;
}
