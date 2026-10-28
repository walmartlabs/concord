package com.walmartlabs.concord.server.security.legacy;

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

import com.typesafe.config.Config;
import com.walmartlabs.concord.server.sdk.security.LegacyAccountSelector;
import com.walmartlabs.concord.server.sdk.security.LegacyIdentityMatcher;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;

import javax.inject.Inject;
import java.util.Optional;

/** Selects only immutable migration enrollment candidates through provider-owned matchers. */
public class LegacyUserLookup {
    private final ProviderRegistry registry;
    private final boolean enabled;

    @Inject
    public LegacyUserLookup(ProviderRegistry registry, Config config) {
        this.registry = registry;
        this.enabled = flag(config, "security.legacy.enabled") && flag(config, "security.legacy.enrollmentEnabled");
    }

    private static boolean flag(Config config, String path) {
        String qualified = "com.walmartlabs.concord.server." + path;
        if (config.hasPath(qualified)) return config.getBoolean(qualified);
        return !config.hasPath(path) || config.getBoolean(path);
    }

    public Optional<LegacyAccountSelector> selector(VerifiedIdentity identity) {
        if (!enabled) return Optional.empty();
        LegacyIdentityMatcher matcher = registry.legacyMatchers().get(identity.identity().providerId());
        return matcher == null ? Optional.empty() : matcher.selector(identity);
    }
}
