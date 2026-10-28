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

import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.LegacyAccountSelector;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OidcLegacyIdentityMatcherTest {
    @Test
    void usesOnlyHistoricalLowercaseEmailLocalSelector() {
        var matcher = new OidcLegacyIdentityMatcher();
        var identity = new VerifiedIdentity(new ExternalIdentityKey("oidc", "https://issuer", "CaseSensitiveSub"),
                new UserProfile("different-name", null, "Name", "Alice@Example.com", Map.of()), Set.of(), Map.of(), null, null);
        assertEquals(new LegacyAccountSelector("alice@example.com", null, "LOCAL"), matcher.selector(identity).orElseThrow());
        var noEmail = new VerifiedIdentity(identity.identity(), new UserProfile("alice@example.com", null, null, null, Map.of()),
                Set.of(), Map.of(), null, null);
        assertTrue(matcher.selector(noEmail).isEmpty());
    }
}
