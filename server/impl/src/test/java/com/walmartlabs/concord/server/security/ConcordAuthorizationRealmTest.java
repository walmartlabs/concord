package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcordAuthorizationRealmTest {
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ConcordAuthorizationRealm realm = new ConcordAuthorizationRealm(authorization);
    private final UUID id = UUID.randomUUID();


    private SubjectPrincipal subject(UUID userId) {
        return new SubjectPrincipal(userId, "alice", null, new AuthenticationContext("oidc", null, null));
    }

    @Test
    void rejectsConflictingAccounts() {
        SimplePrincipalCollection input = new SimplePrincipalCollection(subject(id), "first");
        input.add(subject(UUID.randomUUID()), "oidc");
        assertThrows(IllegalArgumentException.class, () -> PrincipalNormalization.find(input));
        assertThrows(IllegalArgumentException.class, () -> realm.hasRole(input, "admin"));
        verifyNoInteractions(authorization);
    }

    @Test
    void credentialOnlyHasNoUserGrants() {
        SimplePrincipalCollection input = new SimplePrincipalCollection("credential", "apikey");
        assertNull(PrincipalNormalization.find(input));
        assertFalse(realm.hasRole(input, "admin"));
        assertFalse(realm.isPermitted(input, "thing:read"));
        verifyNoInteractions(authorization);
    }

    @Test
    void readsCurrentRolesAndPermissionsWithoutCaching() {
        PrincipalCollection input = new SimplePrincipalCollection(subject(id), "oidc");
        when(authorization.prepare(id)).thenReturn(
                new AuthorizationService.EffectiveAuthorization(Set.of("admin"), Set.of("thing:read")),
                new AuthorizationService.EffectiveAuthorization(Set.of(), Set.of()));
        assertFalse(realm.supports(new UsernamePasswordToken("alice", "password")));
        assertFalse(realm.isAuthorizationCachingEnabled());
        assertTrue(realm.hasRole(input, "admin"));
        assertFalse(realm.isPermitted(input, "thing:read"));
        verify(authorization, times(2)).prepare(id);
    }

    @Test
    void usesPreparedGrantsOnlyWithinScope() {
        PrincipalCollection input = new SimplePrincipalCollection(subject(id), "oidc");
        when(authorization.prepare(id, false)).thenReturn(new AuthorizationService.EffectiveAuthorization(
                Set.of("operator"), Set.of("thing:read")));
        when(authorization.prepare(id)).thenReturn(new AuthorizationService.EffectiveAuthorization(Set.of(), Set.of()));
        try (AuthorizationScope ignored = AuthorizationScope.open(authorization, subject(id), false)) {
            assertTrue(realm.hasRole(input, "operator"));
            assertTrue(realm.isPermitted(input, "thing:read"));
        }
        assertFalse(realm.hasRole(input, "operator"));
        verify(authorization).prepare(id, false);
        verify(authorization).prepare(id);
    }

}
