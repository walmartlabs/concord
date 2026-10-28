package com.walmartlabs.concord.server.plugins.pfedsso;

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

import java.util.Locale;
import java.util.Optional;

/** The historical PFed lookup selected an LDAP account using the parsed subject. */
public class SsoLegacyIdentityMatcher implements LegacyIdentityMatcher {
    @Override
    public String providerId() {
        return SsoRealm.REALM_NAME;
    }

    @Override
    public Optional<LegacyAccountSelector> selector(VerifiedIdentity identity) {
        String subject = identity.identity().subject().trim();
        int at = subject.indexOf('@');
        String username = at < 0 ? subject : subject.substring(0, at);
        String domain = at < 0 ? null : subject.substring(at + 1);
        return Optional.of(new LegacyAccountSelector(username.toLowerCase(Locale.ROOT),
                domain == null ? null : domain.toLowerCase(Locale.ROOT), "LDAP"));
    }
}
