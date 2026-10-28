package com.walmartlabs.concord.server.plugins.oidc;

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
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OidcIdentityTest {
    @Test
    void resolvesVerifiedIssuerAndSubjectWithoutTokenPrincipal() {
        IdentityService identities = mock(IdentityService.class);
        var principal = new SubjectPrincipal(UUID.randomUUID(), "alice", null, new AuthenticationContext("oidc", Instant.now(), null));
        when(identities.resolve(any(), any())).thenReturn(principal);
        Instant observed = Instant.now();
        Instant expires = observed.plusSeconds(120);
        var profile = new UserProfile("Subject", "Alice@example.com", "Alice", null, Map.of("groups", java.util.List.of("dev")));
        var token = new OidcToken(new VerifiedProfile(profile, "https://Issuer", observed, expires));
        var info = new OidcRealm(identities).getAuthenticationInfo(token);
        assertEquals(principal, info.getPrincipals().getPrimaryPrincipal());
        assertNull(info.getPrincipals().oneByType(OidcToken.class));
        var captured = ArgumentCaptor.forClass(VerifiedIdentity.class);
        verify(identities).resolve(captured.capture(), any());
        assertEquals(new ExternalIdentityKey("oidc", "https://Issuer", "Subject"), captured.getValue().identity());
        assertEquals(observed, captured.getValue().attributesObservedAt());
        assertEquals(expires, captured.getValue().attributesExpireAt());
    }

    @Test
    void expiredVerificationCannotResolveIdentity() {
        IdentityService identities = mock(IdentityService.class);
        Instant now = Instant.now();
        var profile = new UserProfile("s", "a@example.com", "A", null, Map.of());
        var token = new OidcToken(new VerifiedProfile(profile, "issuer", now.minusSeconds(2), now.minusSeconds(1)));
        assertThrows(org.apache.shiro.authc.AuthenticationException.class, () -> new OidcRealm(identities).getAuthenticationInfo(token));
        verifyNoInteractions(identities);
    }
}
