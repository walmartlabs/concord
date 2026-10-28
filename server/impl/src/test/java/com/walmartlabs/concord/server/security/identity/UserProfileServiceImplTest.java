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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.UserDirectory;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProfileServiceImplTest {
    @Test
    void selectsOwnedExternalProfileAndFallsBackToStoredSnapshot() {
        UUID id = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        ExternalIdentityKey key = new ExternalIdentityKey("example", "issuer", "subject");
        UserProfile stored = new UserProfile("stored", null, "Stored", null, Map.of());
        UserProfile external = new UserProfile("external", null, "External", null, Map.of());
        IdentityDao dao = mock(IdentityDao.class);
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.id()).thenReturn("example");
        when(directory.acceptsIdentity(key)).thenReturn(true);
        when(directory.get(key)).thenReturn(Optional.of(external), Optional.empty()).thenThrow(new IllegalStateException("offline"));
        ProviderRegistry registry = new ProviderRegistry(Set.of(directory), Set.of(), Set.of(), Set.of());
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(stored, "example", identityId, key)));
        UserProfileServiceImpl service = new UserProfileServiceImpl(dao, registry);
        assertEquals(external, service.get(id));
        assertEquals(stored, service.get(id));
        assertEquals(stored, service.get(id));
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(stored, "example", identityId, null)));
        assertEquals(stored, service.get(id));
        verify(directory, times(3)).get(key);
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(stored, "local", null, null)));
        assertEquals(stored, service.get(id));
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(stored, "example", identityId, key)));
        when(directory.acceptsIdentity(key)).thenReturn(false);
        assertEquals(stored, service.get(id));
        when(dao.profile(id)).thenReturn(Optional.of(new IdentityDao.ProfileSelection(stored, "removed", identityId, key)));
        assertEquals(stored, service.get(id));
        verify(directory, times(3)).get(key);
    }

    @Test
    void absentAccountIsNotFound() {
        IdentityDao dao = mock(IdentityDao.class);
        UserProfileServiceImpl service = new UserProfileServiceImpl(dao, new ProviderRegistry(Set.of(), Set.of(), Set.of(), Set.of()));
        ConcordApplicationException error = assertThrows(ConcordApplicationException.class, () -> service.get(UUID.randomUUID()));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), error.getResponse().getStatus());
    }
}
