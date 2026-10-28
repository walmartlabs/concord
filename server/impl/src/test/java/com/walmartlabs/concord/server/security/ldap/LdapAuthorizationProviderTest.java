package com.walmartlabs.concord.server.security.ldap;

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

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.sdk.security.AuthorizationRequest;
import com.walmartlabs.concord.server.sdk.security.AuthorizationSubjectNotFoundException;
import com.walmartlabs.concord.server.sdk.security.AuthorizationUnavailableException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import org.junit.jupiter.api.Test;

import javax.naming.CommunicationException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LdapAuthorizationProviderTest {
    @Test
    void sourceActivationAndIdentityAreIndependentOfAuthentication() {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        LdapManager manager = mock(LdapManager.class);
        LdapGroupSyncConfiguration sync = mock(LdapGroupSyncConfiguration.class);
        when(sync.getMinAgeLogin()).thenReturn(Duration.ZERO);
        LdapAuthorizationProvider provider = new LdapAuthorizationProvider(cfg, sync, manager, new LdapUserDirectory(manager));
        assertFalse(provider.isActive());
        when(cfg.getDnsSRVName()).thenReturn("_ldap._tcp.example");
        assertTrue(provider.isActive());
        assertEquals(Duration.ofDays(1), provider.defaultMaxAge());
        assertEquals(Duration.ZERO, provider.defaultLoginMaxAge());
        assertTrue(provider.acceptsIdentity(LdapUserDirectory.key("Alice", null)));
        assertFalse(provider.acceptsIdentity(null));
        assertFalse(provider.acceptsIdentity(new ExternalIdentityKey("ldap", "other", "[\"alice\",null]")));
        assertFalse(provider.acceptsIdentity(new ExternalIdentityKey("ldap", "", "[\"Alice\",null]")));
    }

    @Test
    void authoritativeGroupsEmptyMissingAndUnavailableAreDistinct() throws Exception {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        when(cfg.getUrl()).thenReturn("ldap://example");
        LdapManager manager = mock(LdapManager.class);
        LdapAuthorizationProvider provider = new LdapAuthorizationProvider(cfg, mock(LdapGroupSyncConfiguration.class),
                manager, new LdapUserDirectory(manager));
        AuthorizationRequest request = new AuthorizationRequest(UUID.randomUUID(), LdapUserDirectory.key("Alice", "Example"), Map.of(), null, null);
        when(manager.getGroups("alice", "example")).thenReturn(Set.of("group"), Set.of(), null)
                .thenThrow(new CommunicationException("offline"));
        assertEquals(Set.of("group"), provider.resolve(request).groups());
        assertTrue(provider.resolve(request).groups().isEmpty());
        assertThrows(AuthorizationSubjectNotFoundException.class, () -> provider.resolve(request));
        assertThrows(AuthorizationUnavailableException.class, () -> provider.resolve(request));
        verify(manager, times(4)).getGroups("alice", "example");
        verify(manager, never()).getPrincipal(anyString(), any());
    }
}
