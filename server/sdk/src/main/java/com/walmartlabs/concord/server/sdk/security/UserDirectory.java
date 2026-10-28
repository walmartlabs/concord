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

import java.util.Optional;

/** Directory capability independent of authentication and authorization. */
public interface UserDirectory {
    String id();

    /** Checks the identity namespace without I/O. */
    boolean acceptsIdentity(ExternalIdentityKey identity);

    /**
     * Unsupported attributes are invalid and missing entries return empty.
     * Throws {@link DirectoryAmbiguousException} for multiple matches and
     * {@link DirectoryUnavailableException} when no authoritative answer is available.
     */
    Optional<DirectoryEntry> find(DirectoryQuery query);

    /** Missing entries return empty; unavailable directories throw {@link DirectoryUnavailableException}. */
    Optional<UserProfile> get(ExternalIdentityKey identity);
}
