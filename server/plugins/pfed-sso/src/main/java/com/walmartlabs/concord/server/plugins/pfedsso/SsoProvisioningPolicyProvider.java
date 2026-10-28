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

import com.walmartlabs.concord.server.sdk.security.ProvisioningPolicy;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Set;

/** Translates the flat PFed configuration into the core provisioning contract. */
public class SsoProvisioningPolicyProvider implements Provider<ProvisioningPolicy> {
    private final SsoConfiguration cfg;

    @Inject
    public SsoProvisioningPolicyProvider(SsoConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public ProvisioningPolicy get() {
        if (!cfg.isEnabled()) {
            return new ProvisioningPolicy(SsoRealm.REALM_NAME, false, "local", Set.of(), List.of(), "SSO");
        }
        String directory = cfg.getIdentityDirectory();
        Set<String> directories = directory == null || directory.isBlank() ? Set.of() : Set.of(directory);
        return new ProvisioningPolicy(SsoRealm.REALM_NAME, cfg.isAutoCreateUsers(), cfg.getProfileProviderId(),
                directories, cfg.getAuthorizationSources(), "SSO");
    }
}
