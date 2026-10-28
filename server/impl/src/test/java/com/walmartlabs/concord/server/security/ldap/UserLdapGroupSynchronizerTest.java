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

import com.walmartlabs.concord.server.cfg.LdapGroupSyncConfiguration;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserLdapGroupSynchronizerTest {
    @Test
    void pagesBindingsAndContinuesAfterRefreshFailure() {
        UserLdapGroupSynchronizer.Dao dao = mock(UserLdapGroupSynchronizer.Dao.class);
        LdapGroupSyncConfiguration cfg = mock(LdapGroupSyncConfiguration.class);
        AuthorizationService service = mock(AuthorizationService.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(cfg.getFetchLimit()).thenReturn(1);
        when(cfg.getMinAgeSync()).thenReturn(Duration.ofHours(3));
        when(cfg.getInterval()).thenReturn(Duration.ofMinutes(10));
        when(dao.list(eq(1), any(OffsetDateTime.class), isNull())).thenReturn(List.of(first));
        when(dao.list(eq(1), any(OffsetDateTime.class), eq(first))).thenReturn(List.of(second));
        when(dao.list(eq(1), any(OffsetDateTime.class), eq(second))).thenReturn(List.of());
        doThrow(new RuntimeException("offline")).when(service).refresh(first, "ldap", true);
        UserLdapGroupSynchronizer synchronizer = new UserLdapGroupSynchronizer(dao, cfg, service);
        OffsetDateTime before = OffsetDateTime.now().minusHours(3);
        synchronizer.performTask();
        OffsetDateTime after = OffsetDateTime.now().minusHours(3);
        var cutoff = org.mockito.ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(dao).list(eq(1), cutoff.capture(), isNull());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
        verify(service).refresh(first, "ldap", true);
        verify(service).refresh(second, "ldap", true);
        assertEquals(600, synchronizer.getIntervalInSec());
    }
}
