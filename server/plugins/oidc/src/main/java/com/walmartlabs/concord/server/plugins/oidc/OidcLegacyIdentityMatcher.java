package com.walmartlabs.concord.server.plugins.oidc;

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

import com.walmartlabs.concord.server.sdk.security.LegacyAccountSelector;
import com.walmartlabs.concord.server.sdk.security.LegacyIdentityMatcher;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;

import java.util.Optional;

/** The former OIDC realm selected a LOCAL account by lowercased email only. */
public class OidcLegacyIdentityMatcher implements LegacyIdentityMatcher {
    @Override
    public String providerId() {
        return "oidc";
    }

    @Override
    public Optional<LegacyAccountSelector> selector(VerifiedIdentity identity) {
        String email = identity.profile().email();
        return email == null || email.isBlank() ? Optional.empty() :
                Optional.of(new LegacyAccountSelector(email.toLowerCase(), null, "LOCAL"));
    }
}
