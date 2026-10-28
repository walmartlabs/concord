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
import java.util.Objects;

final class SecurityContractValidation {
    private SecurityContractValidation() {
    }

    static String id(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static void observation(Instant observedAt, Instant expireAt) {
        if ((observedAt == null) != (expireAt == null)) {
            throw new IllegalArgumentException("observation timestamps must both be present or absent");
        }
        if (observedAt != null && expireAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("observation expiry precedes observation");
        }
    }
}
