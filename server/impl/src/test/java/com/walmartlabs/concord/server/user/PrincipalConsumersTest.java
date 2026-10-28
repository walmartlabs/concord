package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.console.ConsoleService;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.GroupRef;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.security.legacy.LegacyApiGuard;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrincipalConsumersTest {
    private final UUID id = UUID.randomUUID();
    private final UserEntry account = new UserEntry(id, "alice", "example", "Current name", null, null, null, null, false, null, false);
    private final UserManager users = mock(UserManager.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final DefaultSecurityManager manager = new DefaultSecurityManager();

    private void bind(Object principal) {
        ThreadContext.bind(manager);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(principal, "plugin"));
        ThreadContext.bind(subject);
    }

    @AfterEach
    void cleanup() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void consoleUsesCurrentGrantsForSdkPrincipal() {
        bind(new SubjectPrincipal(id, "old-name", "example", new AuthenticationContext("plugin", null, null)));
        when(users.get(id)).thenReturn(Optional.of(account));
        when(users.getInfo(id)).thenReturn(UserInfo.builder().id(id).username("alice").displayName("Current name").build());
        when(authorization.getGroups(id)).thenReturn(Set.of(new GroupRef("ldap", "current"), new GroupRef("other", "hidden")));
        when(authorization.prepare(id)).thenReturn(new AuthorizationService.EffectiveAuthorization(Set.of("current-role"), Set.of()));
        var dao = mock(ConsoleService.ConsoleServiceDao.class);
        when(dao.listTeams(id)).thenReturn(List.of());
        var legacy = mock(LegacyApiGuard.class);
        when(legacy.isEnabled()).thenReturn(true);
        var console = new ConsoleService(null, null, users, null, null, null, null, null, null, null, null, null, dao, null, legacy, authorization);
        var info = console.getUserInfo();
        assertEquals(id, info.id());
        assertEquals("Current name", info.displayName());
        assertEquals(Set.of("current-role"), Set.copyOf(info.roles()));
        assertEquals(Set.of("current"), info.ldapGroups());
        assertEquals(Set.of(new GroupRef("ldap", "current"), new GroupRef("other", "hidden")), info.authorizationGroups());
        when(legacy.isEnabled()).thenReturn(false);
        var withoutLegacy = console.getUserInfo();
        assertTrue(withoutLegacy.ldapGroups().isEmpty());
        assertEquals(info.authorizationGroups(), withoutLegacy.authorizationGroups());
    }

    @Test
    void identityLookupUsesSameSelfAccessRule() {
        bind(new SubjectPrincipal(id, "alice", null, new AuthenticationContext("plugin", null, null)));
        UserDao dao = mock(UserDao.class);
        IdentityDao identities = mock(IdentityDao.class);
        AccountSelector selectors = mock(AccountSelector.class);
        ExternalIdentityKey key = new ExternalIdentityKey("oidc", "issuer", "subject");
        when(selectors.identityId(key)).thenReturn(id);
        when(dao.get(id)).thenReturn(account);
        var resource = new UserResourceV2(dao, identities, authorization, selectors);
        assertSame(account, resource.getUserByIdentity("oidc", "issuer", "subject"));
        when(selectors.identityId(key)).thenReturn(UUID.randomUUID());
        assertThrows(com.walmartlabs.concord.server.security.UnauthorizedException.class,
                () -> resource.getUserByIdentity("oidc", "issuer", "subject"));
    }

    @Test
    void sdkPrincipalCanReadOwnAccountButNotAnother() {
        bind(new SubjectPrincipal(id, "alice", "example", new AuthenticationContext("plugin", null, null)));
        UserDao dao = mock(UserDao.class);
        when(dao.get(id)).thenReturn(account);
        var resource = new UserResourceV2(dao, mock(IdentityDao.class), authorization, mock(AccountSelector.class));
        assertSame(account, resource.get(id));
        assertThrows(com.walmartlabs.concord.server.security.UnauthorizedException.class, () -> resource.get(UUID.randomUUID()));
    }
}
