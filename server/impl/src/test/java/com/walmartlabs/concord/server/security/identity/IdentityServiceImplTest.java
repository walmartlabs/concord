package com.walmartlabs.concord.server.security.identity;

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

import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.legacy.LegacyUserLookup;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityServiceImplTest {
    private final ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", "subject");
    private final ExternalIdentityKey directoryKey = new ExternalIdentityKey("directory", "", "alice");
    private final UserProfile profile = new UserProfile("alice", null, "Alice", null, Map.of());
    private final AuthenticationContext authentication = new AuthenticationContext("oauth", null, null);
    private final IdentityDao dao = mock(IdentityDao.class);
    private final UserManager users = mock(UserManager.class);
    private final UserDirectory local = mock(UserDirectory.class);
    private final UserDirectory directory = mock(UserDirectory.class);
    private final AuthorizationProvider source = mock(AuthorizationProvider.class);
    private final ProvisioningPolicy policy = new ProvisioningPolicy("oauth", true, "local", Set.of("directory"), List.of("source"), null);

    private ProviderRegistry registry() {
        when(local.id()).thenReturn("local");
        when(directory.id()).thenReturn("directory");
        when(source.id()).thenReturn("source");
        return new ProviderRegistry(Set.of(local, directory), Set.of(source), Set.of(policy), Set.of());
    }

    private VerifiedIdentity verified(Set<IdentityLookup> lookups) {
        return new VerifiedIdentity(key, profile, lookups, Map.of(), null, null);
    }

    private IdentityLookup lookup() {
        return new IdentityLookup("directory", new DirectoryQuery("username", "alice", null), Set.of("source"), false);
    }

    @Test
    void exactKeySkipsDirectoryAndLegacyMatching() {
        ProviderRegistry registry = registry();
        UUID id = UUID.randomUUID();
        when(dao.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), id, key, true)));
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(profile, "local", null, null)));
        LegacyUserLookup legacy = mock(LegacyUserLookup.class);
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, legacy, users, ConfigFactory.empty());
        assertEquals(id, service.resolve(verified(Set.of(lookup())), authentication).userId());
        verify(directory, never()).find(any());
        verifyNoInteractions(legacy);
        verify(dao).observe(eq(id), any());
    }

    @Test
    void retiredKeyCannotProvisionAndDisabledAccountCannotAuthenticate() {
        ProviderRegistry registry = registry();
        UUID id = UUID.randomUUID();
        when(dao.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), id, key, false)));
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, mock(LegacyUserLookup.class), users, ConfigFactory.empty());
        assertThrows(AuthenticationException.class, () -> service.resolve(verified(Set.of()), authentication));
        verify(dao, never()).resolve(any(), any(), any(), anyList(), anyList(), any());
        when(dao.find(key)).thenReturn(Optional.of(new IdentityDao.IdentityRecord(UUID.randomUUID(), id, key, true)));
        doThrow(new AuthenticationException("disabled")).when(dao).observe(eq(id), any());
        assertThrows(AuthenticationException.class, () -> service.resolve(verified(Set.of()), authentication));
    }

    @Test
    void sourceCanUseDirectoryEvenWhenProfileIsLocal() {
        ProviderRegistry registry = registry();
        UUID id = UUID.randomUUID();
        when(dao.find(key)).thenReturn(Optional.empty());
        when(directory.find(any())).thenReturn(Optional.of(new DirectoryEntry(directoryKey, profile)));
        when(directory.acceptsIdentity(directoryKey)).thenReturn(true);
        when(source.acceptsIdentity(directoryKey)).thenReturn(true);
        when(source.isActive()).thenReturn(true);
        when(dao.resolve(any(), any(), any(), anyList(), anyList(), any())).thenReturn(new IdentityDao.Resolution(id, true));
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(profile, "local", null, null)));
        LegacyUserLookup legacy = mock(LegacyUserLookup.class);
        when(legacy.selector(any())).thenReturn(Optional.empty());
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, legacy, users, ConfigFactory.empty());
        assertEquals(id, service.resolve(verified(Set.of(lookup())), authentication).userId());
        verify(dao).resolve(any(), eq(policy), isNull(), eq(List.of(directoryKey)), eq(List.of(new IdentityDao.Binding("source", directoryKey, false))), isNull());
        verify(users).auditIdentityCreation(id);
    }

    @Test
    void requiredMissingLookupIsForbiddenAndUntrustedLookupIsRejected() {
        ProviderRegistry registry = registry();
        when(dao.find(key)).thenReturn(Optional.empty());
        when(directory.find(any())).thenReturn(Optional.empty());
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, mock(LegacyUserLookup.class), users,
                ConfigFactory.parseString("security.authorization.sources.source.required=true"));
        ConcordApplicationException e = assertThrows(ConcordApplicationException.class, () -> service.resolve(verified(Set.of(lookup())), authentication));
        assertEquals(403, e.getResponse().getStatus());
        IdentityLookup untrusted = new IdentityLookup("other", new DirectoryQuery("username", "alice", null), Set.of(), false);
        assertThrows(AuthenticationException.class, () -> service.resolve(verified(Set.of(untrusted)), authentication));
        verify(dao, never()).resolve(any(), any(), any(), anyList(), anyList(), any());
    }
    @Test
    void requiredUnavailableLookupIsServiceUnavailable() {
        ProviderRegistry registry = registry();
        when(dao.find(key)).thenReturn(Optional.empty());
        when(directory.find(any())).thenThrow(new DirectoryUnavailableException("offline"));
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, mock(LegacyUserLookup.class), users,
                ConfigFactory.parseString("security.authorization.sources.source.required=true"));
        ConcordApplicationException e = assertThrows(ConcordApplicationException.class, () -> service.resolve(verified(Set.of(lookup())), authentication));
        assertEquals(503, e.getResponse().getStatus());
        verify(dao, never()).resolve(any(), any(), any(), anyList(), anyList(), any());
    }

    @Test
    void ambiguousDirectoryResultsCannotProvision() {
        ProviderRegistry registry = registry();
        when(dao.find(key)).thenReturn(Optional.empty());
        when(directory.find(any())).thenThrow(new DirectoryAmbiguousException("multiple entries"));
        IdentityServiceImpl service = new IdentityServiceImpl(dao, registry, mock(LegacyUserLookup.class), users, ConfigFactory.empty());
        assertThrows(AuthenticationException.class, () -> service.resolve(verified(Set.of(lookup())), authentication));
        verify(dao, never()).resolve(any(), any(), any(), anyList(), anyList(), any());
    }
}
