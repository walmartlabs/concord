package com.walmartlabs.concord.server.security.provider;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.google.inject.Inject;
import com.walmartlabs.concord.server.sdk.security.AuthorizationProvider;
import com.walmartlabs.concord.server.sdk.security.LegacyIdentityMatcher;
import com.walmartlabs.concord.server.sdk.security.ProvisioningPolicy;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** The independently contributed security capabilities available on this server. */
public class ProviderRegistry {

    private final Map<String, UserDirectory> directories;
    private final Map<String, AuthorizationProvider> authorizationSources;
    private final Map<String, ProvisioningPolicy> policies;
    private final Map<String, LegacyIdentityMatcher> legacyMatchers;

    @Inject
    public ProviderRegistry(Set<UserDirectory> directories,
                            Set<AuthorizationProvider> authorizationSources,
                            Set<ProvisioningPolicy> policies,
                            Set<LegacyIdentityMatcher> legacyMatchers) {
        this.directories = index(directories, UserDirectory::id, "directory");
        this.authorizationSources = index(authorizationSources, AuthorizationProvider::id, "authorization source");
        this.policies = index(policies, ProvisioningPolicy::providerId, "provisioning policy");
        this.legacyMatchers = index(legacyMatchers, LegacyIdentityMatcher::providerId, "legacy matcher");

        for (ProvisioningPolicy policy : policies) {
            requireReference(this.directories, policy.profileProviderId(), policy.providerId(), "profile directory");
            for (String id : policy.allowedIdentityDirectories()) {
                requireReference(this.directories, id, policy.providerId(), "identity directory");
            }
            for (String id : policy.authorizationSources()) {
                requireReference(this.authorizationSources, id, policy.providerId(), "authorization source");
            }
        }
    }

    public Map<String, UserDirectory> directories() {
        return directories;
    }

    public Map<String, AuthorizationProvider> authorizationSources() {
        return authorizationSources;
    }

    public Map<String, ProvisioningPolicy> policies() {
        return policies;
    }

    public Map<String, LegacyIdentityMatcher> legacyMatchers() {
        return legacyMatchers;
    }

    private static <T> Map<String, T> index(Set<T> values, Function<T, String> idFunction, String capability) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = idFunction.apply(value);
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Invalid " + capability + " ID: " + id);
            }
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate " + capability + " ID: " + id);
            }
        }
        return Map.copyOf(result);
    }

    private static void requireReference(Map<String, ?> capabilities, String id, String policyId, String capability) {
        if (!capabilities.containsKey(id)) {
            throw new IllegalArgumentException("Policy '" + policyId + "' references unknown " + capability + ": " + id);
        }
    }
}
