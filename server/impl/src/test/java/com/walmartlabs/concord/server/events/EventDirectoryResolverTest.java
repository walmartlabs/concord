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
package com.walmartlabs.concord.server.events;

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventDirectoryResolverTest {
    @Test
    void resolvesAndProvisionsSelectedDirectory() {
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.id()).thenReturn("custom");
        var key = new ExternalIdentityKey("custom", "authority", "subject");
        var profile = new UserProfile("alice", null, "Alice", null, Map.of());
        var query = new DirectoryQuery("username", "alice", null);
        when(directory.find(query)).thenReturn(Optional.of(new DirectoryEntry(key, profile)));
        when(directory.acceptsIdentity(key)).thenReturn(true);
        var registry = new ProviderRegistry(Set.of(directory), Set.of(), Set.of(), Set.of());
        var identities = mock(IdentityService.class);
        var users = mock(UserManager.class);
        var id = UUID.randomUUID();
        var user = mock(UserEntry.class);
        when(identities.resolve(any(), any())).thenReturn(new SubjectPrincipal(id, "alice", null, new AuthenticationContext("custom", null, null)));
        when(users.get(id)).thenReturn(Optional.of(user));
        var resolver = new EventDirectoryResolver(registry, identities, users);

        assertEquals(user, new EventInitiatorSupplier("author", resolver, "custom", Map.of("author", "alice")).get());
        verify(identities).resolve(new VerifiedIdentity(key, profile, Set.of(), Map.of(), null, null), new AuthenticationContext("custom", null, null));
    }

    @Test
    void unavailableAndMissingDirectoryFailExplicitly() {
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.id()).thenReturn("custom");
        when(directory.find(any())).thenThrow(new DirectoryUnavailableException("offline"));
        var identities = mock(IdentityService.class);
        var users = mock(UserManager.class);
        var resolver = new EventDirectoryResolver(new ProviderRegistry(Set.of(directory), Set.of(), Set.of(), Set.of()), identities, users);

        assertEquals(503, assertThrows(ConcordApplicationException.class, () -> resolver.find("custom", "username", "alice")).getResponse().getStatus());
        assertEquals(503, assertThrows(ConcordApplicationException.class, () -> resolver.find("missing", "username", "alice")).getResponse().getStatus());
        verifyNoInteractions(identities, users);
    }

    @Test
    void missingAuthorDoesNotResolveDirectory() {
        var resolver = mock(EventDirectoryResolver.class);
        assertNull(new EventInitiatorSupplier("author", resolver, "missing", Map.of()).get());
        verifyNoInteractions(resolver);
    }
}
