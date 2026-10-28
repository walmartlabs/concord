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

import com.walmartlabs.concord.server.sdk.security.AuthorizationProvider;
import com.walmartlabs.concord.server.sdk.security.LegacyIdentityMatcher;
import com.walmartlabs.concord.server.sdk.security.ProvisioningPolicy;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderRegistryTest {

    @Test
    void sameIdMayIdentifyDifferentCapabilities() {
        UserDirectory directory = directory("custom");
        AuthorizationProvider source = source("custom");
        LegacyIdentityMatcher matcher = matcher("custom");
        ProvisioningPolicy policy = policy("custom", "custom", Set.of("custom"), List.of("custom"));
        ProviderRegistry registry = new ProviderRegistry(Set.of(directory), Set.of(source), Set.of(policy), Set.of(matcher));

        assertSame(directory, registry.directories().get("custom"));
        assertSame(source, registry.authorizationSources().get("custom"));
        assertSame(policy, registry.policies().get("custom"));
        assertSame(matcher, registry.legacyMatchers().get("custom"));
        assertThrows(UnsupportedOperationException.class, () -> registry.directories().clear());
    }

    @Test
    void duplicateIdsAreRejectedWithinEachCapability() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(directory("same"), directory("same")), Set.of(), Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(), Set.of(source("same"), source("same")), Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(), Set.of(), Set.of(policy("same", "local", Set.of(), List.of()),
                new ProvisioningPolicy("same", false, "local", Set.of(), List.of(), null)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(), Set.of(), Set.of(), Set.of(matcher("same"), matcher("same"))));
    }

    @Test
    void invalidProviderIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(directory(" ")), Set.of(), Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(
                Set.of(), Set.of(source(null)), Set.of(), Set.of()));
    }

    @Test
    void policiesMustReferenceInstalledCapabilitiesEvenWhenAutoCreationIsDisabled() {
        UserDirectory local = directory("local");
        AuthorizationProvider ldap = source("ldap");
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(Set.of(local), Set.of(ldap),
                Set.of(policy("auth", "missing", Set.of(), List.of())), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(Set.of(local), Set.of(ldap),
                Set.of(policy("auth", "local", Set.of("missing"), List.of())), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(Set.of(local), Set.of(ldap),
                Set.of(new ProvisioningPolicy("auth", false, "local", Set.of(), List.of("missing"), null)), Set.of()));
        // Inactive optional sources remain installed; activation is distinct from presence.
        assertDoesNotThrow(() -> new ProviderRegistry(Set.of(local), Set.of(ldap),
                Set.of(policy("auth", "local", Set.of(), List.of("ldap"))), Set.of()));
    }

    private static ProvisioningPolicy policy(String id, String profile, Set<String> directories, List<String> sources) {
        return new ProvisioningPolicy(id, true, profile, directories, sources, null);
    }

    private static UserDirectory directory(String id) {
        UserDirectory result = mock(UserDirectory.class);
        when(result.id()).thenReturn(id);
        return result;
    }

    private static AuthorizationProvider source(String id) {
        AuthorizationProvider result = mock(AuthorizationProvider.class);
        when(result.id()).thenReturn(id);
        return result;
    }

    private static LegacyIdentityMatcher matcher(String id) {
        LegacyIdentityMatcher result = mock(LegacyIdentityMatcher.class);
        when(result.providerId()).thenReturn(id);
        return result;
    }
}
