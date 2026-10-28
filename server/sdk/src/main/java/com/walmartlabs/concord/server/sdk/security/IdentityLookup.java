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

import java.util.Objects;
import java.util.Set;

/** A directory lookup derived from verified claims, for the indicated provisioning needs. */
public record IdentityLookup(String directoryId, DirectoryQuery query, Set<String> authorizationSources, boolean forProfile) {
    public IdentityLookup {
        SecurityContractValidation.id(directoryId, "directoryId");
        Objects.requireNonNull(query, "query");
        authorizationSources = Set.copyOf(authorizationSources);
        authorizationSources.forEach(id -> SecurityContractValidation.id(id, "authorization source"));
    }
}
