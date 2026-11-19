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
 * Explicit outcome of event actor resolution. A rejection or provider failure
 * must not fall through to username matching or another execution identity.
 */
public sealed interface ExternalIdentityResolution {

    /** This resolver does not own the event actor; other resolution may proceed. */
    record Unhandled() implements ExternalIdentityResolution {
    }

    /**
     * The resolver verified the event actor's authoritative identity. This is
     * not authorization to execute or an assertion of current group membership.
     */
    record Resolved(ExternalIdentityRef identity) implements ExternalIdentityResolution {

        public Resolved {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** The resolver owns the actor but cannot accept its identity. */
    record Rejected(String reason) implements ExternalIdentityResolution {

        public Rejected {
            ExternalIdentityRef.requireNonBlank(reason, "reason");
        }
    }
}
