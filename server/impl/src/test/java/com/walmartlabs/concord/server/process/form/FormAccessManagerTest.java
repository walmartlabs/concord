package com.walmartlabs.concord.server.process.form;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapUserInfoProvider;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormAccessManagerTest {

    @Mock
    private LdapUserInfoProvider ldapUserInfoProvider;

    @Mock
    private Supplier<LdapPrincipal> ldapPrincipalSupplier;

    @Test
    void testGetLdapPrincipalGroupsApiKeyRealm() {
        when(ldapUserInfoProvider.getInfo(any(), any(), any()))
                .thenReturn(UserInfoProvider.UserInfo.builder()
                        .groups(Set.of("mock-group"))
                        .build());

        var p = getUserPrincipal("apikey");
        var userGroups = FormAccessManager.getLdapPrincipalGroups(p, ldapUserInfoProvider, ldapPrincipalSupplier);

        assertNotNull(userGroups);
        assertEquals(1, userGroups.size());
        assertEquals("mock-group", userGroups.toArray()[0]);

        verify(ldapUserInfoProvider, times(1)).getInfo(any(), any(), any());
        verify(ldapPrincipalSupplier, times(0)).get();
    }

    @Test
    void testGetLdapPrincipalGroupsLdapRealm() {
        when(ldapPrincipalSupplier.get()).thenReturn(getLdapPrincipal(Set.of("mock-group")));

        var p = getUserPrincipal("ldap");
        var userGroups = FormAccessManager.getLdapPrincipalGroups(p, ldapUserInfoProvider, ldapPrincipalSupplier);

        assertNotNull(userGroups);
        assertEquals(1, userGroups.size());
        assertEquals("mock-group", userGroups.toArray()[0]);

        verify(ldapUserInfoProvider, times(0)).getInfo(any(), any(), any());
        verify(ldapPrincipalSupplier, times(1)).get();
    }

    private static UserPrincipal getUserPrincipal(String realm) {
        return new UserPrincipal(realm, new UserEntry(UUID.randomUUID(), null, null, null, null, null, null, null, false, null, false));
    }

    private static LdapPrincipal getLdapPrincipal(Set<String> groups) {
        return new LdapPrincipal("test", "example.com", null, null, null, null, groups, null);
    }
}
