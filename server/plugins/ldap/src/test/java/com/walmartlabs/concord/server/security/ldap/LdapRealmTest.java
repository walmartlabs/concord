package com.walmartlabs.concord.server.security.ldap;

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

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import javax.naming.ldap.LdapContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LdapRealmTest {
    @Test
    void verifiedBindPrecedesIdentityResolution() throws Exception {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        when(cfg.isAuthenticationEnabled()).thenReturn(true);
        when(cfg.getUrl()).thenReturn("ldap://example");
        LdapManager manager = mock(LdapManager.class);
        when(manager.getPrincipal("Alice", "Example")).thenReturn(new LdapPrincipal("Alice", "Example",
                "cn=Alice,dc=example", "alice@example", "Alice", "alice@example", Set.of(), Map.of()));
        LdapContextFactory factory = mock(LdapContextFactory.class);
        when(factory.getLdapContext("alice@example", "secret")).thenReturn(mock(LdapContext.class));
        IdentityService identities = mock(IdentityService.class);
        SubjectPrincipal principal = new SubjectPrincipal(UUID.randomUUID(), "Alice", "Example",
                new AuthenticationContext("ldap", Instant.now(), null));
        when(identities.resolve(any(), any())).thenReturn(principal);
        LdapRealm realm = new LdapRealm(cfg, identities, factory, manager, mock(AuditLog.class, RETURNS_DEEP_STUBS));
        UsernamePasswordToken token = new UsernamePasswordToken("Alice@Example", "secret");

        AuthenticationInfo result = realm.getAuthenticationInfo(token);
        assertEquals(principal, result.getPrincipals().oneByType(SubjectPrincipal.class));
        assertSame(token, result.getPrincipals().oneByType(UsernamePasswordToken.class));
        assertNull(result.getPrincipals().oneByType(LdapPrincipal.class));
        ArgumentCaptor<VerifiedIdentity> verified = ArgumentCaptor.forClass(VerifiedIdentity.class);
        InOrder order = inOrder(factory, identities);
        order.verify(factory).getLdapContext("alice@example", "secret");
        order.verify(identities).resolve(verified.capture(), any());
        assertEquals(LdapUserDirectory.key("Alice", "Example"), verified.getValue().identity());
    }

    @Test
    void failedOrAnonymousBindNeverResolvesIdentity() throws Exception {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        when(cfg.isAuthenticationEnabled()).thenReturn(true);
        when(cfg.getDnsSRVName()).thenReturn("_ldap._tcp.example");
        LdapManager manager = mock(LdapManager.class);
        when(manager.getPrincipal("Alice", null)).thenReturn(new LdapPrincipal("Alice", null,
                "cn=Alice,dc=example", null, "Alice", null, Set.of(), Map.of()));
        LdapContextFactory factory = mock(LdapContextFactory.class);
        when(factory.getLdapContext("cn=Alice,dc=example", "wrong"))
                .thenThrow(new javax.naming.AuthenticationException("invalid credentials"));
        IdentityService identities = mock(IdentityService.class);
        LdapRealm realm = new LdapRealm(cfg, identities, factory, manager, mock(AuditLog.class));
        assertThrows(AuthenticationException.class, () -> realm.getAuthenticationInfo(new UsernamePasswordToken("Alice", "wrong")));
        assertThrows(AuthenticationException.class, () -> realm.getAuthenticationInfo(new UsernamePasswordToken("Alice", "")));
        verifyNoInteractions(identities);
    }

    @Test
    void disablingAuthenticationDoesNotEnableTheRealm() {
        LdapConfiguration cfg = mock(LdapConfiguration.class);
        IdentityService identities = mock(IdentityService.class);
        LdapRealm realm = new LdapRealm(cfg, identities, mock(LdapContextFactory.class), mock(LdapManager.class), mock(AuditLog.class));
        assertFalse(realm.supports(new UsernamePasswordToken("Alice", "secret")));
        assertNull(realm.doGetAuthenticationInfo(new UsernamePasswordToken("Alice", "secret")));
        verifyNoInteractions(identities);
    }
}
