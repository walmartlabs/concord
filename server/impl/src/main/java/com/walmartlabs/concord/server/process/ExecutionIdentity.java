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

import java.util.Objects;
import java.util.UUID;

/** The account and authentication provenance persisted with an execution. */
public record ExecutionIdentity(UUID userId, String authenticationProviderId) {
    public ExecutionIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(authenticationProviderId, "authenticationProviderId");
        if (authenticationProviderId.isBlank()) {
            throw new IllegalArgumentException("authenticationProviderId must not be blank");
        }
    }
}
